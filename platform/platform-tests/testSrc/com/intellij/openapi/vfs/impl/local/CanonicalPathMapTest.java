// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.PathUtil;
import one.util.streamex.StreamEx;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.Collection;
import java.util.NavigableSet;

import static com.intellij.openapi.util.Pair.pair;
import static com.intellij.openapi.util.io.IoTestUtil.assumeSymLinkCreationIsSupported;
import static java.io.File.separatorChar;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class CanonicalPathMapTest {
  private static final String DIR_ROOT = SystemInfo.isWindows ? "c:\\parent\\root" : "/parent/root";
  private static final String FILE_ROOT = SystemInfo.isWindows ? "c:\\parent\\root.txt" : "/parent/root.txt";
  private static final String CHILD_DIR = separatorChar + "child_dir";
  private static final String CHILD_FILE = separatorChar + "child.txt";

  @Rule public TempDirectory myTempDir = new TempDirectory();

  @Test
  public void flatRootReportedExactlyViaParent() {
    String root = DIR_ROOT;
    CanonicalPathMap map = createCanonicalPathMap(emptyList(), singletonList(root));
    Collection<String> paths = map.mapToOriginalWatchRoots(PathUtil.getParentPath(root), true);
    assertThat(paths).isEmpty();
  }

  @Test
  public void flatRootReportedExactlyViaItself() {
    String root = FILE_ROOT;
    CanonicalPathMap map = createCanonicalPathMap(emptyList(), singletonList(root));
    Collection<String> paths = map.mapToOriginalWatchRoots(root, true);
    assertThat(paths).containsExactly(root);
  }

  @Test
  public void flatRootReportedExactlyViaChild() {
    String root = DIR_ROOT;
    String child = root + CHILD_FILE;
    CanonicalPathMap map = createCanonicalPathMap(emptyList(), singletonList(root));
    Collection<String> paths = map.mapToOriginalWatchRoots(child, true);
    assertThat(paths).containsExactly(child);
  }

  @Test
  public void flatRootReportedInexactlyViaParent() {
    String root = FILE_ROOT;
    CanonicalPathMap map = createCanonicalPathMap(emptyList(), singletonList(root));
    Collection<String> paths = map.mapToOriginalWatchRoots(PathUtil.getParentPath(root), false);
    assertThat(paths).containsExactly(root);
  }

  @Test
  public void flatRootReportedInexactlyViaItself() {
    String root = DIR_ROOT;
    CanonicalPathMap map = createCanonicalPathMap(emptyList(), singletonList(root));
    Collection<String> paths = map.mapToOriginalWatchRoots(root, false);
    assertThat(paths).containsExactly(root);
  }

  @Test
  public void flatRootReportedInexactlyViaChild() {
    String root = DIR_ROOT;
    String child = root + CHILD_DIR;
    CanonicalPathMap map = createCanonicalPathMap(emptyList(), singletonList(root));
    Collection<String> paths = map.mapToOriginalWatchRoots(child, false);
    assertThat(paths).isEmpty();
  }

  @Test
  public void recursiveRootReportedExactlyViaParent() {
    String root = DIR_ROOT;
    CanonicalPathMap map = createCanonicalPathMap(singletonList(root), emptyList());
    Collection<String> paths = map.mapToOriginalWatchRoots(PathUtil.getParentPath(root), true);
    assertThat(paths).isEmpty();
  }

  @Test
  public void recursiveRootReportedExactlyViaItself() {
    String root = DIR_ROOT;
    CanonicalPathMap map = createCanonicalPathMap(singletonList(root), emptyList());
    Collection<String> paths = map.mapToOriginalWatchRoots(root, true);
    assertThat(paths).containsExactly(root);
  }

  @Test
  public void recursiveRootReportedExactlyViaChild() {
    String root = DIR_ROOT;
    String child = root + CHILD_FILE;
    CanonicalPathMap map = createCanonicalPathMap(singletonList(root), emptyList());
    Collection<String> paths = map.mapToOriginalWatchRoots(child, true);
    assertThat(paths).containsExactly(child);
  }

  @Test
  public void recursiveRootReportedInexactlyViaParent() {
    String root = DIR_ROOT;
    CanonicalPathMap map = createCanonicalPathMap(singletonList(root), emptyList());
    Collection<String> paths = map.mapToOriginalWatchRoots(PathUtil.getParentPath(root), false);
    assertThat(paths).containsExactly(root);
  }

  @Test
  public void recursiveRootReportedInexactlyViaItself() {
    String root = DIR_ROOT;
    CanonicalPathMap map = createCanonicalPathMap(singletonList(root), emptyList());
    Collection<String> paths = map.mapToOriginalWatchRoots(root, false);
    assertThat(paths).containsExactly(root);
  }

  @Test
  public void recursiveRootReportedInexactlyViaChild() {
    String root = DIR_ROOT;
    String child = root + CHILD_DIR;
    CanonicalPathMap map = createCanonicalPathMap(singletonList(root), emptyList());
    Collection<String> paths = map.mapToOriginalWatchRoots(child, false);
    assertThat(paths).containsExactly(child);
  }

  @Test
  public void remappedSymLinkReportsOriginalWatchedPath() {
    assumeSymLinkCreationIsSupported();

    // Tests the situation where the watch root is a symlink AND REMAPPED by the native file watcher.
    File realDir = myTempDir.newDirectory("real");
    File symLink = IoTestUtil.createSymLink(realDir.getPath(), myTempDir.getRoot() + "/link");
    File mappedDir = new File(myTempDir.getRoot(), "mapped");

    // Initial symlink map: .../root/link -> .../root/real
    CanonicalPathMap map = createCanonicalPathMap(singletonList(symLink.getPath()), emptyList());

    // REMAP from native file watcher: .../root/mapped -> .../root/real
    map.addMapping(singletonList(pair(mappedDir.getPath(), realDir.getPath())));

    // expected: .../root/mapped/file.txt -> .../root/link/file.txt
    Collection<String> watchedPaths = map.mapToOriginalWatchRoots(new File(mappedDir, "file.txt").getPath(), true);
    assertThat(watchedPaths).containsExactly(new File(symLink, "file.txt").getPath());
  }

  @Test
  public void partialMatchCollision() {
    String root = DIR_ROOT + separatorChar + "sub", collidingRoot = root + "-dir", otherRoot = root + "XXX";
    String probe = root + separatorChar + "file";
    CanonicalPathMap map = createCanonicalPathMap(asList(root, collidingRoot, otherRoot), emptyList());
    Collection<String> watched = map.mapToOriginalWatchRoots(probe, true);
    assertThat(watched).containsExactly(probe);
  }

  private static CanonicalPathMap createCanonicalPathMap(Collection<String> recursive, Collection<String> flat) {
    NavigableSet<String> recursiveSet = StreamEx.of(recursive).into(WatchRootsUtil.createFileNavigableSet());
    NavigableSet<String> flatSet = StreamEx.of(flat).into(WatchRootsUtil.createFileNavigableSet());
    CanonicalPathMap pathMap = new CanonicalPathMap(recursiveSet, flatSet, WatchRootsUtil.createMappingsNavigableSet());
    pathMap.getCanonicalWatchRoots();
    return pathMap;
  }
}