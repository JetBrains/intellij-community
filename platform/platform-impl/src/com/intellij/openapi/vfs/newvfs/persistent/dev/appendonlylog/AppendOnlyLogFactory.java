// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog;

import com.intellij.util.io.dev.mmapped.MMappedFileStorageFactory;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.io.VersionUpdatedException;
import com.intellij.util.io.dev.StorageFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

import static com.intellij.util.io.IOUtil.*;

/**
 * Configurable factory for {@link AppendOnlyLogOverMMappedFile}.
 * All major params and initialization conditions, such as dataFormatVersion check, recovery -- are configurable
 */
@ApiStatus.Internal
public class AppendOnlyLogFactory implements StorageFactory<AppendOnlyLogOverMMappedFile> {
  public static final int DEFAULT_PAGE_SIZE = 4 * MiB;

  private final int pageSize;

  private final int expectedDataVersion;
  private final boolean ensureDataVersion;

  private final boolean prohibitRecovery;

  private AppendOnlyLogFactory(int pageSize,
                               boolean ensureDataVersion,
                               int expectedDataVersion,
                               boolean prohibitRecovery) {
    this.pageSize = pageSize;
    this.expectedDataVersion = expectedDataVersion;
    this.ensureDataVersion = ensureDataVersion;
    this.prohibitRecovery = prohibitRecovery;
  }

  public static AppendOnlyLogFactory withDefaults() {
    return withPageSize(DEFAULT_PAGE_SIZE);
  }

  public static AppendOnlyLogFactory withPageSize(int pageSize) {
    return new AppendOnlyLogFactory(pageSize, false, -1, false);
  }

  public AppendOnlyLogFactory pageSize(int pageSize) {
    return new AppendOnlyLogFactory(pageSize, ensureDataVersion, expectedDataVersion, prohibitRecovery);
  }

  public AppendOnlyLogFactory failIfDataFormatVersionNotMatch(int expectedDataVersion) {
    return new AppendOnlyLogFactory(pageSize, /*ensureDataVersion: */true, expectedDataVersion, prohibitRecovery);
  }

  public AppendOnlyLogFactory ignoreDataFormatVersion() {
    return new AppendOnlyLogFactory(pageSize, /*ensureDataVersion: */false, /*expectedDataVersion: */0, prohibitRecovery);
  }

  public AppendOnlyLogFactory prohibitRecovery() {
    return new AppendOnlyLogFactory(pageSize, ensureDataVersion, expectedDataVersion, /*prohibitRecovery: */true);
  }

  @Override
  public @NotNull AppendOnlyLogOverMMappedFile open(@NotNull Path storagePath) throws IOException {
    return MMappedFileStorageFactory.DEFAULT
      .pageSize(pageSize)
      .wrapStorageSafely(
        storagePath,
        storage -> {
          AppendOnlyLogOverMMappedFile appendOnlyLog = new AppendOnlyLogOverMMappedFile(storage);

          if (prohibitRecovery) {
            if (appendOnlyLog.wasRecoveryNeeded()) {
              throw new CorruptedException("Log[" + storagePath.toAbsolutePath() + "] wasn't properly closed, " +
                                           "and recovery is prohibited -> fail");
            }
          }

          if (ensureDataVersion) {
            int dataFormatVersion = appendOnlyLog.getDataVersion();
            if (dataFormatVersion == 0 && appendOnlyLog.isEmpty()) {
              appendOnlyLog.setDataVersion(expectedDataVersion);
            }
            else if (dataFormatVersion != expectedDataVersion) {
              throw new VersionUpdatedException(storagePath, expectedDataVersion, dataFormatVersion);
            }
          }

          return appendOnlyLog;
        }
      );
  }

  @Override
  public String toString() {
    return "AppendOnlyLogFactory[" +
           "pageSize: " + pageSize +
           (ensureDataVersion ? "ensureDataVersion: " + expectedDataVersion : "") + ", " +
           "prohibitRecovery: " + prohibitRecovery + ']';
  }
}
