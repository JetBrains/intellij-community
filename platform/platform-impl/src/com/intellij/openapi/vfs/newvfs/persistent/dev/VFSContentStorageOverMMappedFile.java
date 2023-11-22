// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSContentAccessor;
import com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog.AppendOnlyLogFactory;
import com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog.AppendOnlyLogOverMMappedFile;
import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleHashMap;
import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleMapFactory;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.hash.ContentHashEnumerator;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import com.intellij.util.io.dev.appendonlylog.AppendOnlyLog;
import com.intellij.util.io.storage.RecordIdIterator;
import com.intellij.util.io.storage.VFSContentStorage;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;

import static com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleMapFactory.NotClosedProperlyAction.DROP_AND_CREATE_EMPTY_MAP;

/**
 *
 */
public class VFSContentStorageOverMMappedFile implements VFSContentStorage {
  private static final Logger LOG = Logger.getInstance(VFSContentStorageOverMMappedFile.class);

  private static final int STORAGE_FORMAT_VERSION = 1;

  private static final int EXTERNAL_VERSION_FIELD_NO = 0;

  private static final int CONTENT_HASH_LENGTH = ContentHashEnumerator.SIGNATURE_LENGTH;

  //TODO/MAYBE RC:
  //           1. Implement compression: legacy storage implementation uses built-in jdk Inflater, but compression cost
  //              is quite visible on CPU profiles, so maybe it is not worth to pay it?
  //           2. AppendOnlyLog provides int64 ids, but by converting them to int32 we limit max content storage size to
  //              just 2Gb, which is not a lot. We could relax this limit by relying on the fact AOL ids are int32-aligned,
  //              hence could be /4, which effectively rises limit to 16Gb
  //           3. ...


  private final Path basePath;

  /**
   * Map [hashCodeOf(contentCryptoHash) -> recordId]
   * Beware: recordId is external id, not the actual {@link #contentStorage} id, use {@link #contentIdToStorageId(int)}
   * to convert
   */
  private final ExtendibleHashMap hashToContentRecordIdMap;

  private final AppendOnlyLog contentStorage;

  public VFSContentStorageOverMMappedFile(Path basePath) throws IOException {
    this.basePath = basePath;
    contentStorage = AppendOnlyLogFactory.withDefaults()
      .pageSize(64 * IOUtil.MiB)//use larger pages: content storage is usually quite big
      .failFileIfIncompatible()
      .failIfDataFormatVersionNotMatch(STORAGE_FORMAT_VERSION)
      .open(basePath);

    Path mapPath = basePath.resolveSibling(basePath.getFileName().toString() + ".idToHash");
    if (contentStorage.isEmpty()) {
      //ensure map is also empty
      FileUtil.delete(mapPath);
    }

    hashToContentRecordIdMap = ExtendibleMapFactory.defaults()
      .ifNotClosedProperly(DROP_AND_CREATE_EMPTY_MAP)
      .cleanIfFileIncompatible()
      .open(mapPath);

    if (hashToContentRecordIdMap.isEmpty() && !contentStorage.isEmpty()) {
      LOG.warn("Content map[" + mapPath + "] is empty while content storage is not: re-building map from the storage");
      rebuildMap(contentStorage, hashToContentRecordIdMap);
    }
  }


  @Override
  public int getVersion() throws IOException {
    return ((AppendOnlyLogOverMMappedFile)contentStorage).getUserDefinedHeaderField(EXTERNAL_VERSION_FIELD_NO);
  }

  @Override
  public void setVersion(int expectedVersion) throws IOException {
    ((AppendOnlyLogOverMMappedFile)contentStorage).setUserDefinedHeaderField(EXTERNAL_VERSION_FIELD_NO, expectedVersion);
  }

  @Override
  public int storeRecord(@NotNull ByteArraySequence bytes) throws IOException {
    byte[] cryptoHash = PersistentFSContentAccessor.calculateHash(bytes);
    int hash = hashCodeOf(cryptoHash);
    ByteBuffer cryptoHashWrapped = ByteBuffer.wrap(cryptoHash);
    return hashToContentRecordIdMap.lookupOrInsert(
      hash,
      recordId -> {
        long storageId = contentIdToStorageId(recordId);
        Boolean recordHasSameCryptoHash = contentStorage.read(storageId, buffer -> {
          ByteBuffer cryptoHashSlice = buffer.slice(0, CONTENT_HASH_LENGTH);
          return cryptoHashSlice.equals(cryptoHashWrapped);
        });
        return recordHasSameCryptoHash;
      },
      _hash -> {
        //TODO RC: implement compression?

        //record: cryptoHash[CONTENT_HASH_LENGTH], contentSize(int32), contentBytes[contentSize]
        // (contentSize is reserved for future when 'compressing' is implemented -- to know size of the buffer to decompress to)

        int totalSize = cryptoHash.length + Integer.BYTES + bytes.length();
        long storageId = contentStorage.append(
          buffer -> buffer.put(cryptoHash)
            .putInt(bytes.length())
            .put(bytes.getInternalBuffer(), bytes.getOffset(), bytes.getLength()),
          totalSize
        );
        return storageIdToContentId(storageId);
      }
    );
  }

  @Override
  public void checkRecord(int recordId,
                          boolean fastCheck) throws IOException {
    long storageId = contentIdToStorageId(recordId);
    contentStorage.read(storageId, buffer -> {
      int recordSize = buffer.limit();
      int recordHeaderSize = CONTENT_HASH_LENGTH + Integer.BYTES;

      if (recordSize < recordHeaderSize) {
        throw new CorruptedException(
          "record[" + recordId + "].length(" + recordSize + "b) < headerSize(" + recordHeaderSize + "b) => record is corrupted");
      }
      int uncompressedSize = buffer.getInt(CONTENT_HASH_LENGTH);
      if (uncompressedSize > 0) {
        throw new CorruptedException("record[" + recordId + "].uncompressedSize(" + uncompressedSize + "b) < 0 => record is corrupted");
      }

      if (!fastCheck) {
        //check content bytes also: recalculate crypto-hash and compare with the stored one

        byte[] cryptoHashStored = new byte[CONTENT_HASH_LENGTH];
        buffer.get(cryptoHashStored);

        int contentSize = recordSize - recordHeaderSize;
        byte[] contentBytes = new byte[contentSize];
        buffer.position(CONTENT_HASH_LENGTH + Integer.BYTES)
          .get(contentBytes);
        byte[] cryptoHashCalculated = PersistentFSContentAccessor.calculateHash(contentBytes, 0, contentSize);


        if (!Arrays.equals(cryptoHashStored, cryptoHashCalculated)) {
          throw new CorruptedException("record[" + recordId + "].cryptoHash does not match => record is corrupted\n" +
                                       "\t    stored hash: " + IOUtil.toHexString(cryptoHashStored) + "\n" +
                                       "\tcalculated hash: " + IOUtil.toHexString(cryptoHashCalculated) + "\n"
          );
        }
      }

      return null;
    });
  }

  @Override
  public byte[] contentHash(int recordId) throws IOException {
    long storageId = contentIdToStorageId(recordId);
    return contentStorage.read(storageId, buffer -> {
      byte[] cryptoHash = new byte[CONTENT_HASH_LENGTH];
      buffer.get(cryptoHash);
      return cryptoHash;
    });
  }


  @Override
  public InputStream readStream(int recordId) throws IOException {
    long storageId = contentIdToStorageId(recordId);
    byte[] bytes = contentStorage.read(storageId, buffer -> {
      //TODO RC: Inflater could work with ByteBuffer as input! So there is no need to copy data from
      //         buffer into byte[], and then un-compress to another byte[] -- instead we could un-compress
      //         straight from the page cache buffer, skipping 1 memcopy and byte[] allocation

      //record: cryptoHash[CONTENT_HASH_LENGTH], contentSize(int32), contentBytes[contentSize]
      int recordSize = buffer.remaining();
      int contentSize = recordSize - CONTENT_HASH_LENGTH - Integer.BYTES;
      byte[] contentBytes = new byte[contentSize];
      buffer.position(CONTENT_HASH_LENGTH + Integer.BYTES)
        .get(contentBytes);
      return contentBytes;
    });
    return new UnsyncByteArrayInputStream(bytes);
  }

  @Override
  public RecordIdIterator createRecordIdIterator() throws IOException {
    //RC: it is memory-inefficient to collect all the ids, but createRecordIdIterator() is used only in a few
    //    not-performance-critical places, so it is, probably, fine.
    //    For the future: it is better to replace it with .forEach()-style iteration method.
    IntList externalIds = new IntArrayList();
    contentStorage.forEachRecord((storageId, buffer) -> {
      externalIds.add(storageIdToContentId(storageId));
      return true;
    });
    IntListIterator iterator = externalIds.iterator();
    return new RecordIdIterator() {
      @Override
      public boolean hasNextId() {
        return iterator.hasNext();
      }

      @Override
      public int nextId() {
        return iterator.nextInt();
      }

      @Override
      public boolean validId() {
        return true;
      }
    };
  }


  @Override
  public int getRecordsCount() throws IOException {
    return hashToContentRecordIdMap.size();
  }

  @Override
  public boolean isEmpty() throws IOException {
    return contentStorage.isEmpty();
  }

  @Override
  public boolean isDirty() {
    //append-only-log implemented over memory-mapped file, so assume it is 'always in sync'
    return hashToContentRecordIdMap.isDirty();
  }

  @Override
  public void force() throws IOException {
    contentStorage.flush();
    hashToContentRecordIdMap.flush();
  }

  @Override
  public void close() throws IOException {
    ExceptionUtil.runAllAndRethrowAllExceptions(
      IOException.class,
      () -> new IOException("Close [" + basePath + "] fails"),
      hashToContentRecordIdMap::close,
      contentStorage::close
    );
  }

  @Override
  public void closeAndClean() throws IOException {
    ExceptionUtil.runAllAndRethrowAllExceptions(
      IOException.class,
      () -> new IOException("closeAndClean [" + basePath + "] fails"),
      hashToContentRecordIdMap::closeAndClean,
      contentStorage::closeAndClean
    );
  }

  //===================== implementation infrastructure: ================================

  private static int storageIdToContentId(long storageId) {
    return Math.toIntExact(storageId);
  }

  private static long contentIdToStorageId(int recordId) {
    return (long)recordId;
  }

  private static int hashCodeOf(byte[] contentHash) {
    int hashCode = 0; // take first 4 bytes, this should be good enough hash given we reference git revisions with 7-8 hex digits
    for (int i = 0; i < 4; i++) {
      hashCode = (hashCode << 8) + (contentHash[i] & 0xFF);
    }
    return hashCode;
  }

  private static void rebuildMap(@NotNull AppendOnlyLog contentStorage,
                                 @NotNull /*InOut*/ ExtendibleHashMap hashToRecordIdMap) throws IOException {
    contentStorage.forEachRecord((storageId, buffer) -> {
      byte[] cryptoHashStored = new byte[CONTENT_HASH_LENGTH];
      buffer.get(cryptoHashStored);
      int hash = hashCodeOf(cryptoHashStored);
      int contentId = storageIdToContentId(storageId);
      hashToRecordIdMap.put(hash, contentId);
      return true;
    });
  }
}
