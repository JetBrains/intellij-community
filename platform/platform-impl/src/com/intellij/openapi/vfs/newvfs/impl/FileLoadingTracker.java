// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.containers.CollectionFactory;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

final class FileLoadingTracker {
  private static final Logger LOG = Logger.getInstance(FileLoadingTracker.class);
  private static final Set<String> ourPaths;
  private static final IntSet ourLeafNameIds;

  static {
    String[] pathsToTrack = System.getProperty("file.system.trace.loading", "").split(";");
    if (pathsToTrack.length == 0) {
      ourPaths = Collections.emptySet();
      ourLeafNameIds = IntSets.EMPTY_SET;
    }
    else {
      ourPaths = CollectionFactory.createFilePathSet(pathsToTrack, SystemInfoRt.isFileSystemCaseSensitive);
      ourLeafNameIds = new IntOpenHashSet(ourPaths.size());
      for (String path : ourPaths) {
        ourLeafNameIds.add(FileNameCache.storeName(StringUtilRt.getShortName(path, '/')));
      }
    }
  }

  static void fileLoaded(@NotNull VirtualDirectoryImpl parent, int nameId) {
    if (ourLeafNameIds.contains(nameId)) {
      String path = parent.getPath() + '/' + FileNameCache.getVFileName(nameId);
      if (ourPaths.contains(path)) {
        LOG.info("Loading " + path, new Throwable());
      }
    }
  }
}
