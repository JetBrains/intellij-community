/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.FilePathHashingStrategy;
import gnu.trove.THashSet;
import gnu.trove.TIntHashSet;

import java.util.Collections;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Set;

/**
 * @author peter
 */
class FileLoadingTracker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.impl.FileLoadingTracker");
  private static final Set<String> ourPaths = new THashSet<>(getPathsToTrack(), FilePathHashingStrategy.create());
  private static final TIntHashSet ourLeafNameIds = new TIntHashSet(ourPaths.stream().mapToInt(path -> FileNameCache.storeName(StringUtil.getShortName(path, '/'))).toArray());

  private static List<String> getPathsToTrack() {
    try {
      return StringUtil.split(Registry.stringValue("file.system.trace.loading"), ";");
    }
    catch (MissingResourceException e) {
      return Collections.emptyList();
    }
  }

  static void fileLoaded(VirtualDirectoryImpl parent, int nameId) {
    if (ourLeafNameIds.contains(nameId)) {
      String path = parent.getPath() + "/" + FileNameCache.getVFileName(nameId).toString();
      if (ourPaths.contains(path)) {
        LOG.info("Loading " + path, new Throwable());
      }
    }
  }
}
