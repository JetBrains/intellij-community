// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.util.PersistentUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VcsLogCachesInvalidator extends CachesInvalidator {
  private static final Logger LOG = Logger.getInstance(VcsLogCachesInvalidator.class);

  public synchronized boolean isValid() {
    if (Files.exists(PersistentUtil.getCorruptionMarkerFile())) {
      boolean deleted = FileUtil.deleteWithRenaming(PersistentUtil.LOG_CACHE);
      if (!deleted) {
        // if we could not delete caches, ensure that corruption marker is still there
        Path corruptionMarkerFile = PersistentUtil.getCorruptionMarkerFile();
        try {
          Files.createDirectories(corruptionMarkerFile.getParent());
          Files.createFile(corruptionMarkerFile);
        }
        catch (IOException e) {
          LOG.warn(e);
        }
      }
      else {
        LOG.info("Deleted Vcs Log caches at " + PersistentUtil.LOG_CACHE);
      }
      return deleted;
    }
    return true;
  }

  @Override
  public void invalidateCaches() {
    if (Files.exists(PersistentUtil.LOG_CACHE)) {
      boolean isEmpty = true;
      try {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(PersistentUtil.LOG_CACHE)) {
          if (stream.iterator().hasNext()) {
            isEmpty = false;
          }
        }
      }
      catch (IOException ignored) {
      }
      if (!isEmpty) {
        try {
          Files.createFile(PersistentUtil.getCorruptionMarkerFile());
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
  }

  @Override
  public @NotNull String getDescription() {
    return VcsLogBundle.message("vcs.log.clear.caches.checkbox.description");
  }

  @Override
  public @NotNull Boolean optionalCheckboxDefaultValue() {
    return Boolean.FALSE;
  }

  public static @NotNull VcsLogCachesInvalidator getInstance() {
    return EP_NAME.findExtension(VcsLogCachesInvalidator.class);
  }
}
