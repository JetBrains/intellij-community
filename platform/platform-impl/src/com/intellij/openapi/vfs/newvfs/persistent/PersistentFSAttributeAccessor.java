// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public static final int NON_EXISTENT_ATTR_RECORD_ID = 0;

  //RC: attributes storage structure:
  //    RecordsTable: attrRecordId -> (size, offset) in DataTable
  //                  if size == -1 -> record is removed
  //    DataTable: (attributeEntry)*
  //                attributeEntry := (entryType: varint), (entryDetail: varint), (entryContent: byte[])?
  //
  //               (entryType: 0,1; entryDetail: fileId; entryContent: none)
  //               -> used if (bulkAttributes==true), only first record ('header') in a list of attributeEntries for given fileId
  //                  fileId -- backref to file records table. Probably, unfinished work, not utilized in any way right now
  //
  //               (entryType: encodedAttrID, entryDetail: size of content, entryContent: byte[size])
  //               -> inline attribute entry, if entryDetail < MAX_SMALL_ATTR_SIZE && myInlineAttributes
  //                  entryContent -- attribute content
  //
  //               (entryType: encodedAttrID, entryDetail: attrRecordId, entryContent: none)
  //               -> non-inline attribute entry, if entryDetail >= MAX_SMALL_ATTR_SIZE || !myInlineAttributes
  //                  EntryDetail-MAX_SMALL_ATTR_SIZE => ref to RecordsTable


  /**
   * RC: this flag influences storage layout, but used nowhere. Seems like it is an unfinished effort to
   * implement something like 'find files with by attributes by scanning through attributes storage first',
   * but now it is hard to say for sure.
   * TODO remove it -- its default value is false, so probably it is not in use anyway
   */
  private final boolean myBulkAttrReadSupport;
  /**
   * If true, store small attribute content (<={@link #MAX_SMALL_ATTR_SIZE}) right in the main attribute record.
   * If false, main record contains only a list ('directory') of attributes references, each refers to dedicated
   * record, with actual attribute content -- and the same strategy is used for attributes with content
   * bigger than {@link #MAX_SMALL_ATTR_SIZE} anyway.
   */
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
  public AttributeInputStream readAttribute(final int fileId,
                                            final @NotNull FileAttribute attribute) throws IOException {
    myLock.readLock().lock();
    try {
      PersistentFSConnection connection = myFSConnection;
      PersistentFSConnection.ensureIdIsValid(fileId);

      final int attrRecordId = fileAttributeRecordId(fileId);
      if (attrRecordId == NON_EXISTENT_ATTR_RECORD_ID) {
        return null;
      }
      final int encodedAttrId = connection.getAttributeId(attribute.getId());

      Storage storage = connection.getAttributes();

      int page = 0;

      try (DataInputStream attrRefs = storage.readStream(attrRecordId)) {
        if (myBulkAttrReadSupport) skipRecordHeader(attrRefs, PersistentFSConnection.RESERVED_ATTR_ID, fileId);

        while (attrRefs.available() > 0) {
          final int attIdOnPage = DataInputOutputUtil.readINT(attrRefs);
          final int attrAddressOrSize = DataInputOutputUtil.readINT(attrRefs);

          if (attIdOnPage != encodedAttrId) {
            if (myInlineAttributes && attrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
              final int inlineAttrSize = attrAddressOrSize;
              attrRefs.skipBytes(inlineAttrSize);
            }
          }
          else {
            if (myInlineAttributes && attrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
              final int inlineAttrSize = attrAddressOrSize;
              final byte[] attrContent = ArrayUtil.newByteArray(inlineAttrSize);
              attrRefs.readFully(attrContent);
              return new AttributeInputStream(new UnsyncByteArrayInputStream(attrContent), myFSConnection.getEnumeratedAttributes());
            }
            page = myInlineAttributes ? attrAddressOrSize - MAX_SMALL_ATTR_SIZE : attrAddressOrSize;
            break;
          }
        }
      }

      if (page == 0) {
        return null;
      }
      AttributeInputStream stream =
        new AttributeInputStream(storage.readStream(page), myFSConnection.getEnumeratedAttributes());
      if (myBulkAttrReadSupport) skipRecordHeader(stream, encodedAttrId, fileId);
      return stream;
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  public boolean hasAttributePage(final int fileId,
                                  final @NotNull FileAttribute attr) throws IOException {
    myLock.readLock().lock();
    try {
      return findAttributePage(fileId, attr, false) != NON_EXISTENT_ATTR_RECORD_ID;
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  /**
   * Opens given attribute of given file for writing
   */
  @NotNull
  public AttributeOutputStream writeAttribute(int fileId,
                                              @NotNull FileAttribute attribute) {
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

  public void deleteAttributes(int fileId) throws IOException {
    myLock.writeLock().lock();
    try {
      PersistentFSConnection connection = myFSConnection;
      int attPage = fileAttributeRecordId(fileId);
      if (attPage != 0) {
        try (final DataInputStream attStream = connection.getAttributes().readStream(attPage)) {
          if (myBulkAttrReadSupport) skipRecordHeader(attStream, PersistentFSConnection.RESERVED_ATTR_ID, fileId);

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

  public void checkAttributesStorageSanity(int fileId,
                                           @NotNull IntList usedAttributeRecordIds,
                                           @NotNull IntList validAttributeIds) throws IOException {
    myLock.readLock().lock();
    try {
      int attributeRecordId = fileAttributeRecordId(fileId);

      assert attributeRecordId >= 0;
      if (attributeRecordId > 0) {
        checkAttributesSanity(attributeRecordId, usedAttributeRecordIds, validAttributeIds);
      }
    }
    finally {
      myLock.readLock().unlock();
    }
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
          //if attribute value could be stored in the directory record inline -> try to (over)write it
          // there:
          rewriteDirectoryRecordWithInlineAttrContent(_out);
        }
        else {
          //if current attribute value can't be stored in directory record -> first remove it from
          //   directory record if it was stored there before:
          int attributeRecordId = findAttributePage(myFileId, myAttribute, true);
          if (myInlineAttributes && attributeRecordId < 0) {
            //attribute was stored inline in directory record -> remove it from there it is now:
            rewriteDirectoryRecordWithInlineAttrContent(new BufferExposingByteArrayOutputStream());
            //now, as entry was removed from directory record, .findAttributePage() must create dedicated
            // record for our attribute, and return its recordId:
            attributeRecordId = findAttributePage(myFileId, myAttribute, /* toWrite: */true);
          }

          if (myBulkAttrReadSupport) {
            BufferExposingByteArrayOutputStream stream = new BufferExposingByteArrayOutputStream();
            out = stream;
            writeRecordHeader(myFSConnection.getAttributeId(myAttribute.getId()), myFileId, this);
            write(_out.getInternalBuffer(), 0, _out.size());
            myFSConnection.getAttributes().writeBytes(attributeRecordId, stream.toByteArraySequence(), myAttribute.isFixedSize());
          }
          else {
            myFSConnection.getAttributes().writeBytes(attributeRecordId, _out.toByteArraySequence(), myAttribute.isFixedSize());
          }
        }
        myModCount.incrementAndGet();
      }
      catch (Throwable t) {
        FSRecords.handleError(t);
        throw new RuntimeException(t);
      }
      finally {
        myLock.writeLock().unlock();
      }
    }

    /**
     * Finds (.myFileId, .myAttribute) entry in storage, and overwrite its content (value) with
     * newAttributeInlinableValue bytes, either in place (if size remains the same), or re-arranging other
     * entries to put updated one in the end (if size was changed)
     *
     * TODO RC: I see no benefits in moving attribute entry to the end of record (almost) always: since
     *          we re-arrange entries anyway, entry could remain there it is now, and entries after it
     *          could be shifted to accommodate new value size as well. This looks not harder than current
     *          impl, but allows for fewer movements at all in some cases
     *
     * newAttributeInlinableValue.size() must be < MAX_SMALL_ATTR_SIZE -- method does not deal with non-inlinable
     * attribute entries.
     *
     * if newAttributeInlinableValue.size()==0 then attribute entry is removed from directory record
     */
    private void rewriteDirectoryRecordWithInlineAttrContent(final @NotNull BufferExposingByteArrayOutputStream newAttributeInlinableValue)
      throws IOException {

      assert newAttributeInlinableValue.size() < MAX_SMALL_ATTR_SIZE : "Only small values could be stored in directory record";
      assert myInlineAttributes:"Attributes could be stored in directory record only if 'inline small attributes' is enabled";

      int recordId = fileAttributeRecordId(myFileId);
      int encodedAttrId = myFSConnection.getAttributeId(myAttribute.getId());

      Storage storage = myFSConnection.getAttributes();
      BufferExposingByteArrayOutputStream unchangedPreviousDirectoryStream = null;

      //if this is a new attr record -> it should be directory record
      boolean createDirectoryRecord = false;

      if (recordId == 0) {
        recordId = storage.createNewRecord();
        updateFileAttributeRecordId(myFileId, recordId);
        createDirectoryRecord = true;
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
            //Read attribute entries (encodedAttrId, address | size, ...), and copy them back to unchangedPreviousDirectoryStream
            // except for given attribute(encodedAttrId) -- which value we try to overwrite, but only if
            // size(new value) == size(old value). i.e. if given attribute is 'big' attribute, then it always fits
            // in, since only a reference (address) to the actual value record is stored in directory record.
            // But if attribute to rewrite is a 'small' attribute, its value is written inline, right in the
            // directory record, if it is of the same size, as was the value previously written.
            while (attrRefs.available() > 0) {
              final int attIdOnPage = DataInputOutputUtil.readINT(attrRefs);
              final int attrAddressOrSize = DataInputOutputUtil.readINT(attrRefs);

              if (attIdOnPage != encodedAttrId) {
                //copy all other attributes as-is:
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
                //if( attIdOnPage == encodedAttrId ) and attribute was written inline -> overwrite it inline
                // _only if its size is unchanged_, so entries after the current one -- remain in place.
                if (attrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
                  if (newAttributeInlinableValue.size() == attrAddressOrSize) {
                    // update inplace when new attr has the same size
                    int remaining = attrRefs.available();
                    storage.replaceBytes(recordId, remainingAtStart - remaining,
                                         newAttributeInlinableValue.toByteArraySequence());
                    return;
                  }
                  //if size was changed -> do not copy current entry into dataStream at all,
                  //  (will add it after all other entries)
                  attrRefs.skipBytes(attrAddressOrSize);
                }
                //... and continue to copy remaining attr entries
              }
            }
          }
          finally {
            if (dataStream != null) dataStream.close();
          }
        }
      }
      //If new attribute wasn't fit inline into the old value place/space, then we need to re-write
      // directory record: first write unchanged content of directory record (all attributes but the
      // current one), and after that write entry for the current attribute and its new value. If new
      // directory record becomes larger than current one, and doesn't fit into its current place in
      // file -> storage.writeStream() deals with it, by re-allocating record in the new place, and
      // write its new content there (and old record probably marked for reclaiming later)
      try (AbstractStorage.StorageDataOutput directoryStream = storage.writeStream(recordId)) {
        if (createDirectoryRecord) {
          if (myBulkAttrReadSupport) writeRecordHeader(PersistentFSConnection.RESERVED_ATTR_ID, myFileId, directoryStream);
        }
        if (unchangedPreviousDirectoryStream != null) {
          directoryStream.write(unchangedPreviousDirectoryStream.getInternalBuffer(), 0, unchangedPreviousDirectoryStream.size());
        }
        if (newAttributeInlinableValue.size() > 0) {
          DataInputOutputUtil.writeINT(directoryStream, encodedAttrId);
          DataInputOutputUtil.writeINT(directoryStream, newAttributeInlinableValue.size());
          directoryStream.write(newAttributeInlinableValue.getInternalBuffer(), 0, newAttributeInlinableValue.size());
        }
      }
    }
  }


  /**
   * Method returns id of attribute record for (fileId, attr). If there is no such record exist, and toWrite=true,
   * then method creates new record for (fileId, attr), and return newly created record id. If toWrite=false,
   * and no such record exist yet -> method return NON_EXISTENT_ATTR_RECORD_ID.
   * If attribute was found in a 'directory' record (i.e. it is 'inlined' attribute) than returned value is
   * -id (negated id) of that directory record.
   *
   * @return attribute record id if that attribute value is stored in dedicated record, or negated
   * record id of a directory entry, if attribute value is inlined in that directory record, or
   * NON_EXISTENT_ATTR_RECORD_ID, if record for the (fileId, attr) is not exist yet, and toWrite=false.
   */
  private int findAttributePage(final int fileId,
                                @NotNull FileAttribute attr,
                                final boolean toWrite) throws IOException {
    PersistentFSConnection connection = myFSConnection;
    PersistentFSConnection.ensureIdIsValid(fileId);

    final int encodedAttrId = connection.getAttributeId(attr.getId());
    final Storage storage = connection.getAttributes();

    int recordId = fileAttributeRecordId(fileId);
    boolean directoryRecord = false;

    if (recordId == NON_EXISTENT_ATTR_RECORD_ID) {
      if (!toWrite) {
        return NON_EXISTENT_ATTR_RECORD_ID;
      }

      recordId = storage.createNewRecord();
      updateFileAttributeRecordId(fileId, recordId);
      directoryRecord = true;
    }
    else {
      try (DataInputStream attrRefs = storage.readStream(recordId)) {
        if (myBulkAttrReadSupport) {
          skipRecordHeader(attrRefs, PersistentFSConnection.RESERVED_ATTR_ID, fileId);
        }

        while (attrRefs.available() > 0) {
          final int attIdOnPage = DataInputOutputUtil.readINT(attrRefs);
          final int attrAddressOrSize = DataInputOutputUtil.readINT(attrRefs);

          if (attIdOnPage == encodedAttrId) {
            if (myInlineAttributes) {
              if (attrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
                //return record id, but negated, so clients understand that it is not dedicated attr record, but
                // directory record, with attribute inlined:
                return -recordId;
              }
              else {
                return attrAddressOrSize - MAX_SMALL_ATTR_SIZE;
              }
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

    return NON_EXISTENT_ATTR_RECORD_ID;
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

  private int fileAttributeRecordId(final int fileId) throws IOException {
    return myFSConnection.getRecords().getAttributeRecordId(fileId);
  }

  private void updateFileAttributeRecordId(final int fileId,
                                           final int attributeRecordId) throws IOException {
    myFSConnection.getRecords().setAttributeRecordId(fileId, attributeRecordId);
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
