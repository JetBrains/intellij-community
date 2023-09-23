// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.io.dev.mmapped.MMappedFileStorageFactory;
import com.intellij.util.io.dev.StorageFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

@ApiStatus.Internal
public class ExtendibleMapFactory implements StorageFactory<ExtendibleHashMap> {
  private static final Logger LOG = Logger.getInstance(ExtendibleMapFactory.class);

  private final int pageSize;
  private final int segmentSize;
  private final @NotNull NotClosedProperlyAction notClosedProperlyAction;

  private ExtendibleMapFactory(int pageSize,
                               int segmentSize,
                               @NotNull NotClosedProperlyAction action) {

    if (Integer.bitCount(segmentSize) != 1) {
      throw new IllegalArgumentException("segmentSize(=" + segmentSize + ") must be power of 2");
    }
    if (segmentSize > pageSize) {
      throw new IllegalArgumentException("segmentSize(=" + segmentSize + ") must be <= pageSize(=" + pageSize + ")");
    }
    if ((pageSize % segmentSize) != 0) {
      throw new IllegalArgumentException("segmentSize(=" + segmentSize + ") must align with pageSize(=" + pageSize + ")");
    }
    this.pageSize = pageSize;
    this.segmentSize = segmentSize;
    notClosedProperlyAction = action;
  }

  public static ExtendibleMapFactory defaults() {
    //approx. 16M (key,value) pairs
    return new ExtendibleMapFactory(
      ExtendibleHashMap.DEFAULT_STORAGE_PAGE_SIZE,
      ExtendibleHashMap.DEFAULT_SEGMENT_SIZE,
      NotClosedProperlyAction.IGNORE_AND_HOPE_FOR_THE_BEST
    );
  }

  public static ExtendibleMapFactory large() {
    //approx. 64M (key,value) pairs
    int segmentSize = ExtendibleHashMap.DEFAULT_SEGMENT_SIZE * 2;
    int pageSize = segmentSize * ExtendibleHashMap.DEFAULT_SEGMENTS_PER_PAGE;
    return new ExtendibleMapFactory(
      pageSize,
      segmentSize,
      NotClosedProperlyAction.IGNORE_AND_HOPE_FOR_THE_BEST
    );
  }

  public ExtendibleMapFactory pageSize(int pageSize) {
    return new ExtendibleMapFactory(pageSize, segmentSize, notClosedProperlyAction);
  }

  public ExtendibleMapFactory segmentSize(int segmentSize) {
    return new ExtendibleMapFactory(pageSize, segmentSize, notClosedProperlyAction);
  }

  public ExtendibleMapFactory ifNotClosedProperly(@NotNull NotClosedProperlyAction action) {
    return new ExtendibleMapFactory(pageSize, segmentSize, action);
  }

  @Override
  public @NotNull ExtendibleHashMap open(@NotNull Path storagePath) throws IOException {
    MMappedFileStorageFactory mappedStorageFactory = MMappedFileStorageFactory.DEFAULT
      .pageSize(pageSize);

    try {
      return mappedStorageFactory
        .wrapStorageSafely(
          storagePath,
          mappedStorage -> {
            ExtendibleHashMap map = new ExtendibleHashMap(mappedStorage, segmentSize);
            if (!map.wasProperlyClosed()) {
              if (notClosedProperlyAction != NotClosedProperlyAction.IGNORE_AND_HOPE_FOR_THE_BEST) {
                throw new CorruptedException(
                  "Storage [" + storagePath + "] was not closed properly, can't be trusted -- could be corrupted");
              }
            }
            return map;
          }
        );
    }
    catch (CorruptedException e) {
      if (notClosedProperlyAction == NotClosedProperlyAction.DROP_AND_CREATE_EMPTY_MAP) {
        LOG.info("[" + storagePath + "]: map is not closed properly, factory strategy[" + notClosedProperlyAction + "]" +
                 " -> trying to drop & re-create map from 0");
        //TODO RC: removing of mmapped file is tricky/unreliable on Windows.
        //         It is better to implement MMappedFileStorage.truncate(), and reuse already opened and truncated mapped
        //         storage for the new EHMap
        FileUtil.delete(storagePath);
        return mappedStorageFactory.wrapStorageSafely(
          storagePath,
          mappedStorage -> new ExtendibleHashMap(mappedStorage, segmentSize)
        );
      }
      else {//if (notClosedProperlyAction == FAIL)
        throw e;
      }
    }
  }

  public enum NotClosedProperlyAction {
    /** Ignore possible inconsistencies -- allow clients to deal with them. Map will have .wasClosedProperly=false */
    IGNORE_AND_HOPE_FOR_THE_BEST,
    /** Throw {@link com.intellij.util.io.CorruptionException} */
    FAIL_SPECTACULARLY,
    /** Drop all the map content, return an empty map. Empty map will have .wasClosedProperly=true (by definition) */
    DROP_AND_CREATE_EMPTY_MAP
  }
}
