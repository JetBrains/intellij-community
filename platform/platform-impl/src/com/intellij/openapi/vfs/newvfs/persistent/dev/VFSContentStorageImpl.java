// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSContentAccessor;
import com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog.AppendOnlyLogFactory;
import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleHashMap;
import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleMapFactory;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.dev.appendonlylog.AppendOnlyLog;
import com.intellij.util.io.storage.RecordIdIterator;
import com.intellij.util.io.storage.VFSContentStorage;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Path;

import static com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleMapFactory.NotClosedProperlyAction.DROP_AND_CREATE_EMPTY_MAP;

/**
 *
 */
public class VFSContentStorageImpl implements VFSContentStorage {
  private static final int STORAGE_FORMAT_VERSION = 1;

  private final Path basePath;

  private final ExtendibleHashMap hashToContentId;
  private final AppendOnlyLog contents;

  public VFSContentStorageImpl(Path basePath) throws IOException {
    this.basePath = basePath;
    contents = AppendOnlyLogFactory.withDefaults()
      .pageSize(64 * IOUtil.MiB)//use larger pages: content storage is usually quite big
      .failFileIfIncompatible()
      .failIfDataFormatVersionNotMatch(STORAGE_FORMAT_VERSION)
      .open(basePath);

    Path mapPath = basePath.resolveSibling(basePath.getFileName().toString() + ".idToHash");
    hashToContentId = ExtendibleMapFactory.defaults()
      .ifNotClosedProperly(DROP_AND_CREATE_EMPTY_MAP)
      .cleanIfFileIncompatible()
      .open(mapPath);
    if (hashToContentId.isEmpty() && !contents.isEmpty()) {
      //FIXME RC: recover hashToContentId from contents storage
    }
  }

  @Override
  public int getVersion() throws IOException {
    //TODO please, implement me
    throw new UnsupportedOperationException("Method is not implemented yet");
  }

  @Override
  public void setVersion(int expectedVersion) throws IOException {
    //TODO please, implement me
    throw new UnsupportedOperationException("Method is not implemented yet");
  }

  @Override
  public int storeRecord(@NotNull ByteArraySequence bytes) throws IOException {
    byte[] cryptoHash = PersistentFSContentAccessor.calculateHash(bytes);
    int hash = 0;//TODO cryptoHash[0..4]...;
    hashToContentId.lookupOrInsert(
      hash,
      contentId -> {
        return contents.read(contentId, buffer -> {
          //TODO return cryptoHash == buffer[0..20]
          return true;
        });
      },
      _hash -> {
        //TODO compress
        //TODO length+cryptoHash+compressedBytes

        long storageId = contents.append(new byte[0]);
        return storageIdToExternalId(storageId);
      }
    );
    //TODO please, implement me
    throw new UnsupportedOperationException("Method is not implemented yet");
  }

  @Override
  public void checkRecord(int recordId, boolean fastCheck) throws IOException {
    //TODO please, implement me
    throw new UnsupportedOperationException("Method is not implemented yet");
  }

  @Override
  public byte[] contentHash(int recordId) throws IOException {
    //TODO please, implement me
    throw new UnsupportedOperationException("Method is not implemented yet");
  }


  @Override
  public DataInputStream readStream(int recordId) throws IOException {
    byte[] bytes = contents.read(recordId, buffer -> {
      //TODO RC: Inflater could work with ByteBuffer as input! So there is no need to copy data from
      //         buffer into byte[], and then un-compress to another byte[] -- instead we could un-compress
      //         straight from the page cache buffer, skipping 1 memcopy and byte[] allocation

      byte[] _bytes = new byte[buffer.remaining()];
      buffer.get(_bytes);
      return _bytes;
    });
    return new DataInputStream(new ByteArrayInputStream(bytes));
  }

  @Override
  public RecordIdIterator createRecordIdIterator() throws IOException {
    //RC: it is memory-inefficient to collect all the ids, but createRecordIdIterator() is used only in a few
    //    not-performance-critical places, so it is, probably, fine.
    //    For the future: it is better to replace it with .forEach()-style iteration method.
    IntList recordIds = new IntArrayList();
    contents.forEachRecord((recordId, buffer) -> {
      recordIds.add(storageIdToExternalId(recordId) );
      return true;
    });
    IntListIterator iterator = recordIds.iterator();
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


  private static int storageIdToExternalId(long logId) {
    return Math.toIntExact(logId);
  }

  @Override
  public int getRecordsCount() throws IOException {
    return hashToContentId.size();
  }

  @Override
  public boolean isEmpty() throws IOException {
    return contents.isEmpty();
  }

  @Override
  public boolean isDirty() {
    //append-only-log implemented over memory-mapped file, so assumed to be 'always in sync'
    return hashToContentId.isDirty();
  }

  @Override
  public void force() throws IOException {
    contents.flush();
    hashToContentId.flush();
  }

  @Override
  public void close() throws IOException {
    ExceptionUtil.runAllAndRethrowAllExceptions(
      IOException.class,
      () -> new IOException("Close [" + basePath + "] fails"),
      hashToContentId::close,
      contents::close
    );
  }

  @Override
  public void closeAndClean() throws IOException {
    ExceptionUtil.runAllAndRethrowAllExceptions(
      IOException.class,
      () -> new IOException("closeAndClean [" + basePath + "] fails"),
      hashToContentId::closeAndClean,
      contents::closeAndClean
    );
  }
}
