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

import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.testFramework.rules.TempDirectory;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

import static com.intellij.openapi.util.Pair.pair;
import static org.assertj.core.api.Assertions.assertThat;

public class CanonicalPathMapTest {
  @Rule public TempDirectory myTempDir = new TempDirectory();

  @Test
  public void testRemappedSymLinkReportsOriginalWatchedPath() throws Exception {
    // Tests the situation where the watch root is a symlink AND REMAPPED by the native file watcher.
    File realDir = myTempDir.newFolder("real");
    File symLink = IoTestUtil.createSymLink(realDir.getPath(), myTempDir.getRoot() + "/link");
    File mappedDir = new File(myTempDir.getRoot(), "mapped");

    // Initial symlink map: /?/root/link_dir -> /?/root/real
    CanonicalPathMap pathMap = new CanonicalPathMap(Collections.singletonList(symLink.getPath()), Collections.emptyList());

    // REMAP from native file watcher: /?/root/mapped -> /?/root/real
    pathMap.addMapping(Collections.singletonList(pair(mappedDir.getPath(), realDir.getPath())));

    Collection<String> watchedPaths = pathMap.getWatchedPaths(new File(mappedDir, "file.txt").getPath(), true, false);
    assertThat(watchedPaths).containsExactly(new File(symLink, "file.txt").getPath());
  }
}