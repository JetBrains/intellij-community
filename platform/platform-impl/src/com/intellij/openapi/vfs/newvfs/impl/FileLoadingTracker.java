// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.CollectionFactory;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
final class FileLoadingTracker {
  private static final Logger LOG = Logger.getInstance(FileLoadingTracker.class);
  private static final Set<String> ourPaths;
  private static final IntSet ourLeafNameIds;

  static {
    List<String> pathsToTrack = StringUtil.split(System.getProperty("file.system.trace.loading", ""), ";");
    if (pathsToTrack.isEmpty()) {
      ourPaths = Collections.emptySet();
      ourLeafNameIds = IntSets.EMPTY_SET;
    }
    else {
      ourPaths = CollectionFactory.createFilePathSet(pathsToTrack);
      ourLeafNameIds = new IntOpenHashSet(ourPaths.size());
      for (String path : ourPaths) {
        ourLeafNameIds.add(FileNameCache.storeName(StringUtil.getShortName(path, '/')));
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
