/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.PathUtil;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class CanonicalPathMapTest {
  private static final String DIR_ROOT = SystemInfo.isWindows ? "c:\\parent\\root" : "/parent/root";
  private static final String FILE_ROOT = SystemInfo.isWindows ? "c:\\parent\\root.txt" : "/parent/root.txt";
  private static final String CHILD_DIR = File.separator + "child_dir";
  private static final String CHILD_FILE = File.separator + "child.txt";

  @Rule public TempDirectory myTempDir = new TempDirectory();

  @Test
  public void flatRootReportedExactlyViaParent() {
    String root = DIR_ROOT;
    CanonicalPathMap map = new CanonicalPathMap(Collections.emptyList(), Collections.singletonList(root));
    Collection<String> paths = map.getWatchedPaths(PathUtil.getParentPath(root), true);
    assertThat(paths).isEmpty();
  }

  @Test
  public void flatRootReportedExactlyViaItself() {
    String root = FILE_ROOT;
    CanonicalPathMap map = new CanonicalPathMap(Collections.emptyList(), Collections.singletonList(root));
    Collection<String> paths = map.getWatchedPaths(root, true);
    assertThat(paths).containsExactly(root);
  }

  @Test
  public void flatRootReportedExactlyViaChild() {
    String root = DIR_ROOT;
    String child = root + CHILD_FILE;
    CanonicalPathMap map = new CanonicalPathMap(Collections.emptyList(), Collections.singletonList(root));
    Collection<String> paths = map.getWatchedPaths(child, true);
    assertThat(paths).containsExactly(child);
  }

  @Test
  public void flatRootReportedInexactlyViaParent() {
    String root = FILE_ROOT;
    CanonicalPathMap map = new CanonicalPathMap(Collections.emptyList(), Collections.singletonList(root));
    Collection<String> paths = map.getWatchedPaths(PathUtil.getParentPath(root), false);
    assertThat(paths).containsExactly(root);
  }

  @Test
  public void flatRootReportedInexactlyViaItself() {
    String root = DIR_ROOT;
    CanonicalPathMap map = new CanonicalPathMap(Collections.emptyList(), Collections.singletonList(root));
    Collection<String> paths = map.getWatchedPaths(root, false);
    assertThat(paths).containsExactly(root);
  }

  @Test
  public void flatRootReportedInexactlyViaChild() {
    String root = DIR_ROOT;
    String child = root + CHILD_DIR;
    CanonicalPathMap map = new CanonicalPathMap(Collections.emptyList(), Collections.singletonList(root));
    Collection<String> paths = map.getWatchedPaths(child, false);
    assertThat(paths).isEmpty();
  }

  @Test
  public void recursiveRootReportedExactlyViaParent() {
    String root = DIR_ROOT;
    CanonicalPathMap map = new CanonicalPathMap(Collections.singletonList(root), Collections.emptyList());
    Collection<String> paths = map.getWatchedPaths(PathUtil.getParentPath(root), true);
    assertThat(paths).isEmpty();
  }

  @Test
  public void recursiveRootReportedExactlyViaItself() {
    String root = DIR_ROOT;
    CanonicalPathMap map = new CanonicalPathMap(Collections.singletonList(root), Collections.emptyList());
    Collection<String> paths = map.getWatchedPaths(root, true);
    assertThat(paths).containsExactly(root);
  }

  @Test
  public void recursiveRootReportedExactlyViaChild() {
    String root = DIR_ROOT;
    String child = root + CHILD_FILE;
    CanonicalPathMap map = new CanonicalPathMap(Collections.singletonList(root), Collections.emptyList());
    Collection<String> paths = map.getWatchedPaths(child, true);
    assertThat(paths).containsExactly(child);
  }

  @Test
  public void recursiveRootReportedInexactlyViaParent() {
    String root = DIR_ROOT;
    CanonicalPathMap map = new CanonicalPathMap(Collections.singletonList(root), Collections.emptyList());
    Collection<String> paths = map.getWatchedPaths(PathUtil.getParentPath(root), false);
    assertThat(paths).containsExactly(root);
  }

  @Test
  public void recursiveRootReportedInexactlyViaItself() {
    String root = DIR_ROOT;
    CanonicalPathMap map = new CanonicalPathMap(Collections.singletonList(root), Collections.emptyList());
    Collection<String> paths = map.getWatchedPaths(root, false);
    assertThat(paths).containsExactly(root);
  }

  @Test
  public void recursiveRootReportedInexactlyViaChild() {
    String root = DIR_ROOT;
    String child = root + CHILD_DIR;
    CanonicalPathMap map = new CanonicalPathMap(Collections.singletonList(root), Collections.emptyList());
    Collection<String> paths = map.getWatchedPaths(child, false);
    assertThat(paths).containsExactly(child);
  }

  @Test
  public void remappedSymLinkReportsOriginalWatchedPath() throws IOException {
    // Tests the situation where the watch root is a symlink AND REMAPPED by the native file watcher.
    File realDir = myTempDir.newFolder("real");
    File symLink = IoTestUtil.createSymLink(realDir.getPath(), myTempDir.getRoot() + "/link");
    File mappedDir = new File(myTempDir.getRoot(), "mapped");

    // Initial symlink map: /?/root/link_dir -> /?/root/real
    CanonicalPathMap pathMap = new CanonicalPathMap(Collections.singletonList(symLink.getPath()), Collections.emptyList());

    // REMAP from native file watcher: /?/root/mapped -> /?/root/real
    pathMap.addMapping(Collections.singletonList(Pair.pair(mappedDir.getPath(), realDir.getPath())));

    Collection<String> watchedPaths = pathMap.getWatchedPaths(new File(mappedDir, "file.txt").getPath(), true);
    assertThat(watchedPaths).containsExactly(new File(symLink, "file.txt").getPath());
  }
}