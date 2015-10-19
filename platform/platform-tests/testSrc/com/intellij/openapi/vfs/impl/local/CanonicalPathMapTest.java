/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class CanonicalPathMapTest {

  /**
   * This tests the situation where the watch root is a symlink AND REMAPPED by the native file watcher.
   */
  @Test
  public void testRemappedSymLinkReportsOriginalWatchedPath() throws Exception {
    File rootDir = IoTestUtil.createTestDir("root");
    try {
      File realDir = IoTestUtil.createTestDir(rootDir, "real");
      File symLink = IoTestUtil.createSymLink(realDir.getPath(), rootDir.getPath() + "/linkdir");

      // Initial symlink map: /???/root/linkdir -> /???/root/real
      CanonicalPathMap canonicalPathMap = new CanonicalPathMap(ContainerUtil.newArrayList(symLink.getPath()),
                                                               ContainerUtil.<String>newArrayList());

      // REMAP from native file watcher
      List<Pair<String, String>> mappings = ContainerUtil.newArrayList();
      mappings.add(Pair.pair("/foo/bar", realDir.getPath()));
      canonicalPathMap.addMapping(mappings);

      Collection<String> mappedPaths = canonicalPathMap.getWatchedPaths("/foo/bar/file.txt", true, false);
      Assert.assertEquals(mappedPaths.size(), 1);
      String reportedWatchPath = mappedPaths.iterator().next();
      String expectedWatchPath = symLink.getPath() + "/file.txt";
      Assert.assertEquals(expectedWatchPath, reportedWatchPath);
    } finally {
      IoTestUtil.delete(rootDir);
    }
  }
}
