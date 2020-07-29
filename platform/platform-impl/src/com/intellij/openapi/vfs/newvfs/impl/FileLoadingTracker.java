// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.FilePathHashingStrategy;
import gnu.trove.THashSet;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Set;

/**
 * @author peter
 */
final class FileLoadingTracker {
  private static final Logger LOG = Logger.getInstance(FileLoadingTracker.class);
  private static final Set<String> ourPaths = new THashSet<>(getPathsToTrack(), FilePathHashingStrategy.create());
  private static final TIntHashSet ourLeafNameIds = new TIntHashSet(ourPaths.stream().mapToInt(path -> FileNameCache.storeName(StringUtil.getShortName(path, '/'))).toArray());

  @NotNull
  private static List<String> getPathsToTrack() {
    try {
      return StringUtil.split(Registry.stringValue("file.system.trace.loading"), ";");
    }
    catch (MissingResourceException e) {
      return Collections.emptyList();
    }
  }

  static void fileLoaded(@NotNull VirtualDirectoryImpl parent, int nameId) {
    if (ourLeafNameIds.contains(nameId)) {
      String path = parent.getPath() + "/" + FileNameCache.getVFileName(nameId).toString();
      if (ourPaths.contains(path)) {
        LOG.info("Loading " + path, new Throwable());
      }
    }
  }
}
