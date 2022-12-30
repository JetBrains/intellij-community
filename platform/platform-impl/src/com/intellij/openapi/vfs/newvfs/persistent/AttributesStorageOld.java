// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.Disposer;
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

/**
 *
 */
public class AttributesStorageOld extends AbstractAttributesStorage {

  /**
   * RC: this flag influences storage layout, but used nowhere. Seems like it is an unfinished effort to
   * implement something like 'find files with by attributes by scanning through attributes storage first',
   * but now it is hard to say for sure.
   * TODO remove it -- its default value is false, so probably it is not in use anyway
   */
  private final boolean bulkAttrReadSupport;
  /**
   * If true, store small attribute content (<={@link #INLINE_ATTRIBUTE_SMALLER_THAN}) right in the main attribute record.
   * If false, main record contains only a list ('directory') of attributes references, each refers to dedicated
   * record, with actual attribute content -- and the same strategy is used for attributes with content
   * bigger than {@link #INLINE_ATTRIBUTE_SMALLER_THAN} anyway.
   */
  private final boolean inlineAttributes;

  @NotNull
  private final Storage attributesBlobStorage;

  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final AtomicInteger modCount = new AtomicInteger();


  protected AttributesStorageOld(final boolean bulkAttrReadSupport,
                                 final boolean inlineAttributes,
                                 final @NotNull Storage attributesBlobStorage) {
    this.bulkAttrReadSupport = bulkAttrReadSupport;
    this.inlineAttributes = inlineAttributes;

    this.attributesBlobStorage = attributesBlobStorage;
  }

  @Override
  public int getVersion() throws IOException {
    return attributesBlobStorage.getVersion();
  }

  @Override
  public void setVersion(final int version) throws IOException {
    attributesBlobStorage.setVersion(version);
  }

  @Override
  public @Nullable AttributeInputStream readAttribute(final PersistentFSConnection connection,
                                                      final int fileId,
                                                      final @NotNull FileAttribute attribute) throws IOException {
    lock.readLock().lock();
    try {
      PersistentFSConnection.ensureIdIsValid(fileId);

      final int attrRecordId = fileAttributeRecordId(connection, fileId);
      if (attrRecordId == NON_EXISTENT_ATTR_RECORD_ID) {
        return null;
      }
      final int encodedAttrId = connection.getAttributeId(attribute.getId());

      int page = 0;

      try (DataInputStream attrRefs = attributesBlobStorage.readStream(attrRecordId)) {
        if (bulkAttrReadSupport) skipRecordHeader(attrRefs, PersistentFSConnection.RESERVED_ATTR_ID, fileId);

        while (attrRefs.available() > 0) {
          final int attIdOnPage = DataInputOutputUtil.readINT(attrRefs);
          final int attrAddressOrSize = DataInputOutputUtil.readINT(attrRefs);

          if (attIdOnPage != encodedAttrId) {
            if (inlineAttributes && attrAddressOrSize < INLINE_ATTRIBUTE_SMALLER_THAN) {
              final int inlineAttrSize = attrAddressOrSize;
              attrRefs.skipBytes(inlineAttrSize);
            }
          }
          else {
            if (inlineAttributes && attrAddressOrSize < INLINE_ATTRIBUTE_SMALLER_THAN) {
              final int inlineAttrSize = attrAddressOrSize;
              final byte[] attrContent = ArrayUtil.newByteArray(inlineAttrSize);
              attrRefs.readFully(attrContent);
              return new AttributeInputStream(new UnsyncByteArrayInputStream(attrContent), connection.getEnumeratedAttributes());
            }
            page = inlineAttributes ? attrAddressOrSize - INLINE_ATTRIBUTE_SMALLER_THAN : attrAddressOrSize;
            break;
          }
        }
      }

      if (page == 0) {
        return null;
      }
      AttributeInputStream stream =
        new AttributeInputStream(attributesBlobStorage.readStream(page), connection.getEnumeratedAttributes());
      if (bulkAttrReadSupport) skipRecordHeader(stream, encodedAttrId, fileId);
      return stream;
    }
    finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public boolean hasAttributePage(final PersistentFSConnection connection,
                                  final int fileId,
                                  final @NotNull FileAttribute attribute) throws IOException {
    lock.readLock().lock();
    try {
      return findAttributePage(connection, fileId, attribute, false) != NON_EXISTENT_ATTR_RECORD_ID;
    }
    finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Opens given attribute of given file for writing
   */
  @Override
  @NotNull
  public AttributeOutputStream writeAttribute(final PersistentFSConnection connection,
                                              final int fileId,
                                              final @NotNull FileAttribute attribute) {
    return new AttributeOutputStream(
      new AttributeOutputStreamImpl(connection, fileId, attribute),
      connection.getEnumeratedAttributes()
    );
  }

  @Override
  public void deleteAttributes(final PersistentFSConnection connection,
                               final int fileId) throws IOException {
    lock.writeLock().lock();
    try {
      int attPage = fileAttributeRecordId(connection, fileId);
      if (attPage != 0) {
        try (final DataInputStream attStream = attributesBlobStorage.readStream(attPage)) {
          if (bulkAttrReadSupport) skipRecordHeader(attStream, PersistentFSConnection.RESERVED_ATTR_ID, fileId);

          while (attStream.available() > 0) {
            DataInputOutputUtil.readINT(attStream);// Attribute ID;
            int attAddressOrSize = DataInputOutputUtil.readINT(attStream);

            if (inlineAttributes) {
              if (attAddressOrSize < INLINE_ATTRIBUTE_SMALLER_THAN) {
                attStream.skipBytes(attAddressOrSize);
                continue;
              }
              attAddressOrSize -= INLINE_ATTRIBUTE_SMALLER_THAN;
            }
            attributesBlobStorage.deleteRecord(attAddressOrSize);
          }
        }
        attributesBlobStorage.deleteRecord(attPage);
      }
    }
    finally {
      lock.writeLock().unlock();
    }
  }

  @Override
  public int getLocalModificationCount() {
    return modCount.get();
  }


  @Override
  public void checkAttributesStorageSanity(final PersistentFSConnection connection,
                                           final int fileId,
                                           final @NotNull IntList usedAttributeRecordIds,
                                           final @NotNull IntList validAttributeIds) throws IOException {
    lock.readLock().lock();
    try {
      int attributeRecordId = fileAttributeRecordId(connection, fileId);

      assert attributeRecordId >= 0;
      if (attributeRecordId > 0) {
        checkAttributesSanity(attributeRecordId, usedAttributeRecordIds, validAttributeIds);
      }
    }
    finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public boolean isDirty() {
    return attributesBlobStorage.isDirty();
  }

  @Override
  public void force() throws IOException {
    attributesBlobStorage.force();
  }

  @Override
  public void close() throws IOException {
    Disposer.dispose(attributesBlobStorage);
  }
  /* ==================================== implementation =================================================================== */

  private final class AttributeOutputStreamImpl extends DataOutputStream {
    private final PersistentFSConnection connection;
    @NotNull
    private final FileAttribute myAttribute;
    private final int myFileId;

    private AttributeOutputStreamImpl(final PersistentFSConnection connection,
                                      final int fileId,
                                      final @NotNull FileAttribute attribute) {
      super(new BufferExposingByteArrayOutputStream());
      this.connection = connection;
      myFileId = fileId;
      myAttribute = attribute;
    }

    @Override
    public void close() throws IOException {
      super.close();

      lock.writeLock().lock();
      try {
        final BufferExposingByteArrayOutputStream _out = (BufferExposingByteArrayOutputStream)out;

        if (inlineAttributes && _out.size() < INLINE_ATTRIBUTE_SMALLER_THAN) {
          //if attribute value could be stored in the directory record inline -> try to (over)write it
          // there:
          rewriteDirectoryRecordWithInlineAttrContent(_out);
        }
        else {
          //if current attribute value can't be stored in directory record -> first remove it from
          //   directory record if it was stored there before:
          int attributeRecordId = findAttributePage(connection, myFileId, myAttribute, true);
          if (inlineAttributes && attributeRecordId < 0) {
            //attribute was stored inline in directory record -> remove it from there it is now:
            rewriteDirectoryRecordWithInlineAttrContent(new BufferExposingByteArrayOutputStream());
            //now, as entry was removed from directory record, .findAttributePage() must create dedicated
            // record for our attribute, and return its recordId:
            attributeRecordId = findAttributePage(connection, myFileId, myAttribute, /* toWrite: */true);
          }

          if (bulkAttrReadSupport) {
            BufferExposingByteArrayOutputStream stream = new BufferExposingByteArrayOutputStream();
            out = stream;
            writeRecordHeader(connection.getAttributeId(myAttribute.getId()), myFileId, this);
            write(_out.getInternalBuffer(), 0, _out.size());
            attributesBlobStorage.writeBytes(attributeRecordId, stream.toByteArraySequence(), myAttribute.isFixedSize());
          }
          else {
            attributesBlobStorage.writeBytes(attributeRecordId, _out.toByteArraySequence(), myAttribute.isFixedSize());
          }
        }
        modCount.incrementAndGet();
      }
      catch (Throwable t) {
        FSRecords.handleError(t);
        throw new RuntimeException(t);
      }
      finally {
        lock.writeLock().unlock();
      }
    }

    /**
     * Finds (.myFileId, .myAttribute) entry in storage, and overwrite its content (value) with
     * newAttributeInlinableValue bytes, either in place (if size remains the same), or re-arranging other
     * entries to put updated one in the end (if size was changed)
     * <p>
     * TODO RC: I see no benefits in moving attribute entry to the end of record (almost) always: since
     *          we re-arrange entries anyway, entry could remain there it is now, and entries after it
     *          could be shifted to accommodate new value size as well. This looks not harder than current
     *          impl, but allows for fewer movements at all in some cases
     * <p>
     * newAttributeInlinableValue.size() must be < MAX_SMALL_ATTR_SIZE -- method does not deal with non-inlinable
     * attribute entries.
     * <p>
     * if newAttributeInlinableValue.size()==0 then attribute entry is removed from directory record
     */
    private void rewriteDirectoryRecordWithInlineAttrContent(final @NotNull BufferExposingByteArrayOutputStream newAttributeInlinableValue)
      throws IOException {

      assert newAttributeInlinableValue.size() < INLINE_ATTRIBUTE_SMALLER_THAN : "Only small values could be stored in directory record";
      assert inlineAttributes : "Attributes could be stored in directory record only if 'inline small attributes' is enabled";

      int recordId = fileAttributeRecordId(connection, myFileId);
      int encodedAttrId = connection.getAttributeId(myAttribute.getId());

      BufferExposingByteArrayOutputStream unchangedPreviousDirectoryStream = null;

      //if this is a new attr record -> it should be directory record
      boolean createDirectoryRecord = false;

      if (recordId == 0) {
        recordId = attributesBlobStorage.createNewRecord();
        updateFileAttributeRecordId(connection, myFileId, recordId);
        createDirectoryRecord = true;
      }
      else {
        try (DataInputStream attrRefs = attributesBlobStorage.readStream(recordId)) {

          DataOutputStream dataStream = null;

          try {
            final int remainingAtStart = attrRefs.available();
            if (bulkAttrReadSupport) {
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

                if (attrAddressOrSize < INLINE_ATTRIBUTE_SMALLER_THAN) {
                  byte[] b = ArrayUtil.newByteArray(attrAddressOrSize);
                  attrRefs.readFully(b);
                  dataStream.write(b);
                }
              }
              else {
                //if( attIdOnPage == encodedAttrId ) and attribute was written inline -> overwrite it inline
                // _only if its size is unchanged_, so entries after the current one -- remain in place.
                if (attrAddressOrSize < INLINE_ATTRIBUTE_SMALLER_THAN) {
                  if (newAttributeInlinableValue.size() == attrAddressOrSize) {
                    // update inplace when new attr has the same size
                    int remaining = attrRefs.available();
                    attributesBlobStorage.replaceBytes(recordId, remainingAtStart - remaining,
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
      try (AbstractStorage.StorageDataOutput directoryStream = attributesBlobStorage.writeStream(recordId)) {
        if (createDirectoryRecord) {
          if (bulkAttrReadSupport) writeRecordHeader(PersistentFSConnection.RESERVED_ATTR_ID, myFileId, directoryStream);
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
  private int findAttributePage(final PersistentFSConnection connection,
                                final int fileId,
                                final @NotNull FileAttribute attribute,
                                final boolean toWrite) throws IOException {
    PersistentFSConnection.ensureIdIsValid(fileId);

    final int encodedAttrId = connection.getAttributeId(attribute.getId());

    int recordId = fileAttributeRecordId(connection, fileId);
    boolean directoryRecord = false;

    if (recordId == NON_EXISTENT_ATTR_RECORD_ID) {
      if (!toWrite) {
        return NON_EXISTENT_ATTR_RECORD_ID;
      }

      recordId = attributesBlobStorage.createNewRecord();
      updateFileAttributeRecordId(connection, fileId, recordId);
      directoryRecord = true;
    }
    else {
      try (DataInputStream attrRefs = attributesBlobStorage.readStream(recordId)) {
        if (bulkAttrReadSupport) {
          skipRecordHeader(attrRefs, PersistentFSConnection.RESERVED_ATTR_ID, fileId);
        }

        while (attrRefs.available() > 0) {
          final int attIdOnPage = DataInputOutputUtil.readINT(attrRefs);
          final int attrAddressOrSize = DataInputOutputUtil.readINT(attrRefs);

          if (attIdOnPage == encodedAttrId) {
            if (inlineAttributes) {
              if (attrAddressOrSize < INLINE_ATTRIBUTE_SMALLER_THAN) {
                //return record id, but negated, so clients understand that it is not dedicated attr record, but
                // directory record, with attribute inlined:
                return -recordId;
              }
              else {
                return attrAddressOrSize - INLINE_ATTRIBUTE_SMALLER_THAN;
              }
            }
            else {
              return attrAddressOrSize;
            }
          }
          else {
            if (inlineAttributes && attrAddressOrSize < INLINE_ATTRIBUTE_SMALLER_THAN) {
              attrRefs.skipBytes(attrAddressOrSize);
            }
          }
        }
      }
    }

    if (toWrite) {
      try (AbstractStorage.AppenderStream appender = attributesBlobStorage.appendStream(recordId)) {
        if (bulkAttrReadSupport) {
          if (directoryRecord) {
            DataInputOutputUtil.writeINT(appender, PersistentFSConnection.RESERVED_ATTR_ID);
            DataInputOutputUtil.writeINT(appender, fileId);
          }
        }

        DataInputOutputUtil.writeINT(appender, encodedAttrId);
        int attrAddress = attributesBlobStorage.createNewRecord();
        DataInputOutputUtil.writeINT(appender, inlineAttributes ? attrAddress + INLINE_ATTRIBUTE_SMALLER_THAN : attrAddress);
        PersistentFSConnection.REASONABLY_SMALL.myAttrPageRequested = true;
        return attrAddress;
      }
      finally {
        PersistentFSConnection.REASONABLY_SMALL.myAttrPageRequested = false;
      }
    }

    return NON_EXISTENT_ATTR_RECORD_ID;
  }


  private static void writeRecordHeader(final int recordTag,
                                        final int fileId,
                                        final @NotNull DataOutputStream appender) throws IOException {
    DataInputOutputUtil.writeINT(appender, recordTag);
    DataInputOutputUtil.writeINT(appender, fileId);
  }

  private static void skipRecordHeader(final DataInputStream refs,
                                       final int expectedRecordTag,
                                       final int expectedFileId) throws IOException {
    int attId = DataInputOutputUtil.readINT(refs);// attrId
    assert attId == expectedRecordTag || expectedRecordTag == 0;
    int fileId = DataInputOutputUtil.readINT(refs);// fileId
    assert expectedFileId == fileId || expectedFileId == 0;
  }

  private int fileAttributeRecordId(final PersistentFSConnection connection,
                                    final int fileId) throws IOException {
    return connection.getRecords().getAttributeRecordId(fileId);
  }

  private void updateFileAttributeRecordId(final PersistentFSConnection connection,
                                           final int fileId,
                                           final int attributeRecordId) throws IOException {
    connection.getRecords().setAttributeRecordId(fileId, attributeRecordId);
  }

  private void checkAttributesSanity(final int attributeRecordId,
                                     final @NotNull IntList usedAttributeRecordIds,
                                     final @NotNull IntList validAttributeIds) throws IOException {
    assert !usedAttributeRecordIds.contains(attributeRecordId);
    usedAttributeRecordIds.add(attributeRecordId);

    try (DataInputStream dataInputStream = attributesBlobStorage.readStream(attributeRecordId)) {
      if (bulkAttrReadSupport) skipRecordHeader(dataInputStream, 0, 0);

      while (dataInputStream.available() > 0) {
        int attId = DataInputOutputUtil.readINT(dataInputStream);

        if (!validAttributeIds.contains(attId)) {
          //assert !getNames().valueOf(attId).isEmpty();
          validAttributeIds.add(attId);
        }

        int attDataRecordIdOrSize = DataInputOutputUtil.readINT(dataInputStream);

        if (inlineAttributes) {
          if (attDataRecordIdOrSize < INLINE_ATTRIBUTE_SMALLER_THAN) {
            dataInputStream.skipBytes(attDataRecordIdOrSize);
            continue;
          }
          else {
            attDataRecordIdOrSize -= INLINE_ATTRIBUTE_SMALLER_THAN;
          }
        }
        assert !usedAttributeRecordIds.contains(attDataRecordIdOrSize);
        usedAttributeRecordIds.add(attDataRecordIdOrSize);

        attributesBlobStorage.checkSanity(attDataRecordIdOrSize);
      }
    }
  }
}
