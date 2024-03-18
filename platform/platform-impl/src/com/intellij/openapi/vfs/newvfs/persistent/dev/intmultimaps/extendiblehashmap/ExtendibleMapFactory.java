// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleHashMap.HeaderLayout;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.dev.mmapped.MMappedFileStorageFactory;
import com.intellij.util.io.dev.StorageFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleHashMap.HeaderLayout.STATIC_HEADER_SIZE;
import static java.nio.ByteOrder.nativeOrder;
import static java.nio.file.StandardOpenOption.READ;

@ApiStatus.Internal
public class ExtendibleMapFactory implements StorageFactory<ExtendibleHashMap> {
  private static final Logger LOG = Logger.getInstance(ExtendibleMapFactory.class);

  private final int pageSize;
  private final int segmentSize;
  private final @NotNull NotClosedProperlyAction notClosedProperlyAction;
  private final boolean eagerlyCheckFileCompatibility;
  /**
   * If eager check finds file is incompatible:
   * true: just clean/delete it and open empty storage on top of empty file
   * false: throw an IOException
   */
  private final boolean cleanFileIfIncompatible;


  private ExtendibleMapFactory(int pageSize,
                               int segmentSize,
                               @NotNull NotClosedProperlyAction notClosedProperlyAction,
                               boolean eagerlyCheckFileCompatibility,
                               boolean cleanFileIfIncompatible) {
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
    this.notClosedProperlyAction = notClosedProperlyAction;
    this.eagerlyCheckFileCompatibility = eagerlyCheckFileCompatibility;
    this.cleanFileIfIncompatible = cleanFileIfIncompatible;
  }

  public static ExtendibleMapFactory mediumSize() {
    //approx. 16M (key,value) pairs
    return new ExtendibleMapFactory(
      ExtendibleHashMap.DEFAULT_STORAGE_PAGE_SIZE,
      ExtendibleHashMap.DEFAULT_SEGMENT_SIZE,
      NotClosedProperlyAction.IGNORE_AND_HOPE_FOR_THE_BEST,
      /*eagerlyCheckFileCompatibility: */ true,
      /*cleanFileIfIncompatible:       */ false
    );
  }

  public static ExtendibleMapFactory largeSize() {
    //approx. 64M (key,value) pairs
    int segmentSize = ExtendibleHashMap.DEFAULT_SEGMENT_SIZE * 2;
    int pageSize = segmentSize * ExtendibleHashMap.DEFAULT_SEGMENTS_PER_PAGE;
    return new ExtendibleMapFactory(
      pageSize,
      segmentSize,
      NotClosedProperlyAction.IGNORE_AND_HOPE_FOR_THE_BEST,
      /*eagerlyCheckFileCompatibility: */ true,
      /*cleanFileIfIncompatible:       */ false
    );
  }

  public ExtendibleMapFactory pageSize(int pageSize) {
    return new ExtendibleMapFactory(pageSize, segmentSize, notClosedProperlyAction, eagerlyCheckFileCompatibility, cleanFileIfIncompatible);
  }

  public ExtendibleMapFactory segmentSize(int segmentSize) {
    return new ExtendibleMapFactory(pageSize, segmentSize, notClosedProperlyAction, eagerlyCheckFileCompatibility, cleanFileIfIncompatible);
  }

  public ExtendibleMapFactory ifNotClosedProperly(@NotNull NotClosedProperlyAction action) {
    return new ExtendibleMapFactory(pageSize, segmentSize, action, eagerlyCheckFileCompatibility, cleanFileIfIncompatible);
  }

  public ExtendibleMapFactory cleanIfFileIncompatible() {
    return new ExtendibleMapFactory(
      pageSize, segmentSize, notClosedProperlyAction,
      /*eagerlyCheckFileCompatibility: */true, /*cleanFileIfIncompatible: */true
    );
  }

  public ExtendibleMapFactory failIfFileIncompatible() {
    return new ExtendibleMapFactory(
      pageSize, segmentSize, notClosedProperlyAction,
      /*eagerlyCheckFileCompatibility: */true, /*cleanFileIfIncompatible: */false
    );
  }


  @Override
  public @NotNull ExtendibleHashMap open(@NotNull Path storagePath) throws IOException {
    if (eagerlyCheckFileCompatibility) {
      //Check the crucial file params (file type, impl version, segment size...) _before_ open mmapped storage
      // over it. It could be troubling to unmap & delete file already mapped into memory (especially on Windows),
      // so it pays off to check crucial file parameters eagerly, before the mapping, and either fail or clean
      // the file while it is not mapped yet:
      long size = Files.exists(storagePath) ? Files.size(storagePath) : 0L;
      if (size > 0) {
        checkCrucialFileHeaderParamsEagerly(storagePath);
      }
    }

    MMappedFileStorageFactory mappedStorageFactory = MMappedFileStorageFactory.withDefaults()
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

  private void checkCrucialFileHeaderParamsEagerly(@NotNull Path storagePath) throws IOException {
    ByteBuffer headerBuffer = ByteBuffer.allocate(STATIC_HEADER_SIZE)
      .order(nativeOrder())
      .clear();

    try (FileChannel channel = FileChannel.open(storagePath, READ)) {
      try {
        int actuallyRead = channel.read(headerBuffer);
        if (actuallyRead != STATIC_HEADER_SIZE) {
          throw new CorruptedException(
            "[" + storagePath + "]: file is not empty, but < HEADER_SIZE(=" + STATIC_HEADER_SIZE + ")"
          );
        }

        int magicWord = HeaderLayout.magicWord(headerBuffer);
        if (magicWord != ExtendibleHashMap.MAGIC_WORD) {
          throw new IOException(
            "[" + storagePath + "] is of incorrect type: " +
            ".magicWord(=" + magicWord + ", '" + IOUtil.magicWordToASCII(magicWord) + "') " +
            "!= " + ExtendibleHashMap.MAGIC_WORD + " expected"
          );
        }

        int implVersion = HeaderLayout.version(headerBuffer);
        if (implVersion != ExtendibleHashMap.IMPLEMENTATION_VERSION) {
          throw new IOException(
            "[" + storagePath + "]: version(=" + implVersion + ") " +
            "!= current impl version(=" + ExtendibleHashMap.IMPLEMENTATION_VERSION + ")"
          );
        }

        int segmentSize = HeaderLayout.segmentSize(headerBuffer);
        if (segmentSize != this.segmentSize) {
          throw new IOException(
            "[" + storagePath + "]: segmentSize(=" + this.segmentSize + ") != segmentSize(=" + segmentSize + ")" +
            " storage was initialized with"
          );
        }
      }
      catch (IOException ex) {
        if (cleanFileIfIncompatible) {
          LOG.info("[" + storagePath + "] is incompatible with current format " +
                   "-> delete it, and pretend never seen it incompatible " +
                   "(incompatibility: " + ex.getMessage() + ")"
          );
          FileUtil.delete(storagePath);
        }
        else {
          throw ex;
        }
      }

      byte fileStatus = HeaderLayout.fileStatus(headerBuffer);
      boolean wasProperlyClosed = (fileStatus == HeaderLayout.FILE_STATUS_PROPERLY_CLOSED);
      if (!wasProperlyClosed) {
        switch (notClosedProperlyAction) {
          case FAIL_SPECTACULARLY -> throw new CorruptedException(
            "Storage [" + storagePath + "] was not closed properly, can't be trusted -- could be corrupted"
          );
          case DROP_AND_CREATE_EMPTY_MAP -> {
            LOG.info("[" + storagePath + "] was not closed properly, can't be trusted -> delete it, and re-create from 0");
            FileUtil.delete(storagePath);
          }
          case IGNORE_AND_HOPE_FOR_THE_BEST -> {
            //nothing
          }
        }
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
