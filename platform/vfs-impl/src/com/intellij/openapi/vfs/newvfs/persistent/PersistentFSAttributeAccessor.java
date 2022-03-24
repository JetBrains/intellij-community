// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.vfs.newvfs.AttributeInputStream;
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import com.intellij.util.io.storage.AbstractStorage;
import com.intellij.util.io.storage.Storage;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

final class PersistentFSAttributeAccessor {
  // Vfs small attrs: store inline:
  // file's AttrId -> [size, capacity] attr record (RESERVED_ATTR_ID fileId)? (attrId ((smallAttrSize smallAttrData) | (attr record)) )
  // other attr record: (AttrId, fileId) ? attrData
  private static final int MAX_SMALL_ATTR_SIZE = 64;

  private final boolean myBulkAttrReadSupport;
  private final boolean myInlineAttributes;
  private final PersistentFSConnection myFSConnection;
  private final ReadWriteLock myLock = new ReentrantReadWriteLock();
  private final AtomicInteger myModCount = new AtomicInteger();

  PersistentFSAttributeAccessor(boolean bulkAttrReadSupport, boolean inlineAttributes, @NotNull PersistentFSConnection connection) {
    myBulkAttrReadSupport = bulkAttrReadSupport;
    myInlineAttributes = inlineAttributes;
    myFSConnection = connection;
  }

  @Nullable
  AttributeInputStream readAttribute(int fileId, @NotNull FileAttribute attribute) throws IOException {
    myLock.readLock().lock();
    try {
      PersistentFSConnection connection = myFSConnection;
      PersistentFSConnection.ensureIdIsValid(fileId);

      int recordId = getRecords().getAttributeRecordId(fileId);
      if (recordId == 0) return null;
      int encodedAttrId = connection.getAttributeId(attribute.getId());

      Storage storage = connection.getAttributes();

      int page = 0;

      try (DataInputStream attrRefs = storage.readStream(recordId)) {
        if (myBulkAttrReadSupport) skipRecordHeader(attrRefs, PersistentFSConnection.RESERVED_ATTR_ID, fileId);

        while (attrRefs.available() > 0) {
          final int attIdOnPage = DataInputOutputUtil.readINT(attrRefs);
          final int attrAddressOrSize = DataInputOutputUtil.readINT(attrRefs);

          if (attIdOnPage != encodedAttrId) {
            if (myInlineAttributes && attrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
              attrRefs.skipBytes(attrAddressOrSize);
            }
          }
          else {
            if (myInlineAttributes && attrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
              byte[] b = ArrayUtil.newByteArray(attrAddressOrSize);
              attrRefs.readFully(b);
              return new AttributeInputStream(new UnsyncByteArrayInputStream(b), myFSConnection.getEnumeratedAttributes());
            }
            page = myInlineAttributes ? attrAddressOrSize - MAX_SMALL_ATTR_SIZE : attrAddressOrSize;
            break;
          }
        }
      }

      if (page == 0) {
        return null;
      }
      AttributeInputStream stream = new AttributeInputStream(connection.getAttributes().readStream(page), myFSConnection.getEnumeratedAttributes());
      if (myBulkAttrReadSupport) skipRecordHeader(stream, encodedAttrId, fileId);
      return stream;
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  boolean hasAttributePage(int fileId, @NotNull FileAttribute attr) throws IOException {
    myLock.readLock().lock();
    try {
      return findAttributePage(fileId, attr, false) != 0;
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  @NotNull
  AttributeOutputStream writeAttribute(int fileId, @NotNull FileAttribute attribute) {
    AttributeOutputStream stream = new AttributeOutputStream(new AttributeOutputStreamImpl(fileId, attribute),
                                                             myFSConnection.getEnumeratedAttributes());
    if (attribute.isVersioned()) {
      try {
        DataInputOutputUtil.writeINT(stream, attribute.getVersion());
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return stream;
  }

  private int findAttributePage(int fileId,
                                @NotNull FileAttribute attr,
                                boolean toWrite) throws IOException {
    PersistentFSConnection connection = myFSConnection;
    PersistentFSConnection.ensureIdIsValid(fileId);

    int recordId = getRecords().getAttributeRecordId(fileId);
    int encodedAttrId = connection.getAttributeId(attr.getId());
    boolean directoryRecord = false;

    Storage storage = connection.getAttributes();

    if (recordId == 0) {
      if (!toWrite) return 0;

      recordId = storage.createNewRecord();
      getRecords().setAttributeRecordId(fileId, recordId);
      directoryRecord = true;
    }
    else {
      try (DataInputStream attrRefs = storage.readStream(recordId)) {
        if (myBulkAttrReadSupport) skipRecordHeader(attrRefs, PersistentFSConnection.RESERVED_ATTR_ID, fileId);

        while (attrRefs.available() > 0) {
          final int attIdOnPage = DataInputOutputUtil.readINT(attrRefs);
          final int attrAddressOrSize = DataInputOutputUtil.readINT(attrRefs);

          if (attIdOnPage == encodedAttrId) {
            if (myInlineAttributes) {
              return attrAddressOrSize < MAX_SMALL_ATTR_SIZE ? -recordId : attrAddressOrSize - MAX_SMALL_ATTR_SIZE;
            }
            else {
              return attrAddressOrSize;
            }
          }
          else {
            if (myInlineAttributes && attrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
              attrRefs.skipBytes(attrAddressOrSize);
            }
          }
        }
      }
    }

    if (toWrite) {
      try (AbstractStorage.AppenderStream appender = storage.appendStream(recordId)) {
        if (myBulkAttrReadSupport) {
          if (directoryRecord) {
            DataInputOutputUtil.writeINT(appender, PersistentFSConnection.RESERVED_ATTR_ID);
            DataInputOutputUtil.writeINT(appender, fileId);
          }
        }

        DataInputOutputUtil.writeINT(appender, encodedAttrId);
        int attrAddress = storage.createNewRecord();
        DataInputOutputUtil.writeINT(appender, myInlineAttributes ? attrAddress + MAX_SMALL_ATTR_SIZE : attrAddress);
        PersistentFSConnection.REASONABLY_SMALL.myAttrPageRequested = true;
        return attrAddress;
      }
      finally {
        PersistentFSConnection.REASONABLY_SMALL.myAttrPageRequested = false;
      }
    }

    return 0;
  }

  void deleteAttributes(int id) throws IOException {
    myLock.writeLock().lock();
    try {
      PersistentFSConnection connection = myFSConnection;
      int attPage = getRecords().getAttributeRecordId(id);
      if (attPage != 0) {
        try (final DataInputStream attStream = connection.getAttributes().readStream(attPage)) {
          if (myBulkAttrReadSupport) skipRecordHeader(attStream, PersistentFSConnection.RESERVED_ATTR_ID, id);

          while (attStream.available() > 0) {
            DataInputOutputUtil.readINT(attStream);// Attribute ID;
            int attAddressOrSize = DataInputOutputUtil.readINT(attStream);

            if (myInlineAttributes) {
              if (attAddressOrSize < MAX_SMALL_ATTR_SIZE) {
                attStream.skipBytes(attAddressOrSize);
                continue;
              }
              attAddressOrSize -= MAX_SMALL_ATTR_SIZE;
            }
            connection.getAttributes().deleteRecord(attAddressOrSize);
          }
        }
        connection.getAttributes().deleteRecord(attPage);
      }
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  public int getLocalModificationCount() {
    return myModCount.get();
  }

  private final class AttributeOutputStreamImpl extends DataOutputStream {
    @NotNull
    private final FileAttribute myAttribute;
    private final int myFileId;

    private AttributeOutputStreamImpl(final int fileId, @NotNull FileAttribute attribute) {
      super(new BufferExposingByteArrayOutputStream());
      myFileId = fileId;
      myAttribute = attribute;
    }

    @Override
    public void close() throws IOException {
      super.close();

      myLock.writeLock().lock();
      try {
        final BufferExposingByteArrayOutputStream _out = (BufferExposingByteArrayOutputStream)out;

        if (myInlineAttributes && _out.size() < MAX_SMALL_ATTR_SIZE) {
          rewriteDirectoryRecordWithAttrContent(_out);
          myModCount.incrementAndGet();
        }
        else {
          myModCount.incrementAndGet();
          int page = findAttributePage(myFileId, myAttribute, true);
          if (myInlineAttributes && page < 0) {
            rewriteDirectoryRecordWithAttrContent(new BufferExposingByteArrayOutputStream());
            page = findAttributePage(myFileId, myAttribute, true);
          }

          if (myBulkAttrReadSupport) {
            BufferExposingByteArrayOutputStream stream = new BufferExposingByteArrayOutputStream();
            out = stream;
            writeRecordHeader(myFSConnection.getAttributeId(myAttribute.getId()), myFileId, this);
            write(_out.getInternalBuffer(), 0, _out.size());
            myFSConnection.getAttributes().writeBytes(page, stream.toByteArraySequence(), myAttribute.isFixedSize());
          }
          else {
            myFSConnection.getAttributes().writeBytes(page, _out.toByteArraySequence(), myAttribute.isFixedSize());
          }
        }
      }
      catch (Throwable t) {
        FSRecords.handleError(t);
        throw new RuntimeException(t);
      }
      finally {
        myLock.writeLock().unlock();
      }
    }

    private void rewriteDirectoryRecordWithAttrContent(@NotNull BufferExposingByteArrayOutputStream _out) throws IOException {
      int recordId = getRecords().getAttributeRecordId(myFileId);
      assert myInlineAttributes;
      int encodedAttrId = myFSConnection.getAttributeId(myAttribute.getId());

      Storage storage = myFSConnection.getAttributes();
      BufferExposingByteArrayOutputStream unchangedPreviousDirectoryStream = null;
      boolean directoryRecord = false;


      if (recordId == 0) {
        recordId = storage.createNewRecord();
        getRecords().setAttributeRecordId(myFileId, recordId);
        directoryRecord = true;
      }
      else {
        try (DataInputStream attrRefs = storage.readStream(recordId)) {

          DataOutputStream dataStream = null;

          try {
            final int remainingAtStart = attrRefs.available();
            if (myBulkAttrReadSupport) {
              unchangedPreviousDirectoryStream = new BufferExposingByteArrayOutputStream();
              dataStream = new DataOutputStream(unchangedPreviousDirectoryStream);
              int attId = DataInputOutputUtil.readINT(attrRefs);
              assert attId == PersistentFSConnection.RESERVED_ATTR_ID;
              int fileId = DataInputOutputUtil.readINT(attrRefs);
              assert myFileId == fileId;

              writeRecordHeader(attId, fileId, dataStream);
            }
            while (attrRefs.available() > 0) {
              final int attIdOnPage = DataInputOutputUtil.readINT(attrRefs);
              final int attrAddressOrSize = DataInputOutputUtil.readINT(attrRefs);

              if (attIdOnPage != encodedAttrId) {
                if (dataStream == null) {
                  unchangedPreviousDirectoryStream = new BufferExposingByteArrayOutputStream();
                  dataStream = new DataOutputStream(unchangedPreviousDirectoryStream);
                }
                DataInputOutputUtil.writeINT(dataStream, attIdOnPage);
                DataInputOutputUtil.writeINT(dataStream, attrAddressOrSize);

                if (attrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
                  byte[] b = ArrayUtil.newByteArray(attrAddressOrSize);
                  attrRefs.readFully(b);
                  dataStream.write(b);
                }
              }
              else {
                if (attrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
                  if (_out.size() == attrAddressOrSize) {
                    // update inplace when new attr has the same size
                    int remaining = attrRefs.available();
                    storage.replaceBytes(recordId, remainingAtStart - remaining,
                                         _out.toByteArraySequence());
                    return;
                  }
                  attrRefs.skipBytes(attrAddressOrSize);
                }
              }
            }
          }
          finally {
            if (dataStream != null) dataStream.close();
          }
        }
      }

      try (AbstractStorage.StorageDataOutput directoryStream = storage.writeStream(recordId)) {
        if (directoryRecord) {
          if (myBulkAttrReadSupport) writeRecordHeader(PersistentFSConnection.RESERVED_ATTR_ID, myFileId, directoryStream);
        }
        if (unchangedPreviousDirectoryStream != null) {
          directoryStream.write(unchangedPreviousDirectoryStream.getInternalBuffer(), 0, unchangedPreviousDirectoryStream.size());
        }
        if (_out.size() > 0) {
          DataInputOutputUtil.writeINT(directoryStream, encodedAttrId);
          DataInputOutputUtil.writeINT(directoryStream, _out.size());
          directoryStream.write(_out.getInternalBuffer(), 0, _out.size());
        }
      }
    }
  }

  private static void writeRecordHeader(int recordTag, int fileId, @NotNull DataOutputStream appender) throws IOException {
    DataInputOutputUtil.writeINT(appender, recordTag);
    DataInputOutputUtil.writeINT(appender, fileId);
  }

  private static void skipRecordHeader(DataInputStream refs, int expectedRecordTag, int expectedFileId) throws IOException {
    int attId = DataInputOutputUtil.readINT(refs);// attrId
    assert attId == expectedRecordTag || expectedRecordTag == 0;
    int fileId = DataInputOutputUtil.readINT(refs);// fileId
    assert expectedFileId == fileId || expectedFileId == 0;
  }


  void checkAttributesStorageSanity(int id,
                                    @NotNull IntList usedAttributeRecordIds,
                                    @NotNull IntList validAttributeIds) throws IOException {
    myLock.readLock().lock();
    try {
      int attributeRecordId = getRecords().getAttributeRecordId(id);

      assert attributeRecordId >= 0;
      if (attributeRecordId > 0) {
        checkAttributesSanity(attributeRecordId, usedAttributeRecordIds, validAttributeIds);
      }
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  @NotNull
  private PersistentFSRecordsStorage getRecords() {
    return myFSConnection.getRecords();
  }

  private void checkAttributesSanity(int attributeRecordId,
                                     @NotNull IntList usedAttributeRecordIds,
                                     @NotNull IntList validAttributeIds) throws IOException {
    assert !usedAttributeRecordIds.contains(attributeRecordId);
    usedAttributeRecordIds.add(attributeRecordId);

    try (DataInputStream dataInputStream = myFSConnection.getAttributes().readStream(attributeRecordId)) {
      if (myBulkAttrReadSupport) skipRecordHeader(dataInputStream, 0, 0);

      while (dataInputStream.available() > 0) {
        int attId = DataInputOutputUtil.readINT(dataInputStream);

        if (!validAttributeIds.contains(attId)) {
          //assert !getNames().valueOf(attId).isEmpty();
          validAttributeIds.add(attId);
        }

        int attDataRecordIdOrSize = DataInputOutputUtil.readINT(dataInputStream);

        if (myInlineAttributes) {
          if (attDataRecordIdOrSize < MAX_SMALL_ATTR_SIZE) {
            dataInputStream.skipBytes(attDataRecordIdOrSize);
            continue;
          }
          else {
            attDataRecordIdOrSize -= MAX_SMALL_ATTR_SIZE;
          }
        }
        assert !usedAttributeRecordIds.contains(attDataRecordIdOrSize);
        usedAttributeRecordIds.add(attDataRecordIdOrSize);

        myFSConnection.getAttributes().checkSanity(attDataRecordIdOrSize);
      }
    }
  }
}
