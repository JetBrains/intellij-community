// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.*;

import static com.intellij.openapi.util.io.IoTestUtil.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class VirtualFileVisitorTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory tempDir = new TempDirectory();

  private VirtualFile myRoot;

  @Before
  public void setUp() {
    assumeSymLinkCreationIsSupported();

    File d1 = createTestDir(tempDir.getRoot(), "d1");
    File d11 = createTestDir(d1, "d11");
    createTestFile(d11, "f11.1");
    createTestFile(d11, "f11.2");
    createTestFile(d1, "f1.1");
    createTestDir(d1, "d12");
    File d13 = createTestDir(d1, "d13");
    createTestFile(d13, "f13.1");
    createTestFile(d13, "f13.2");
    File d2 = createTestDir(tempDir.getRoot(), "d2");
    createTestFile(d2, "f2.1");
    createTestFile(d2, "f2.2");
    File d3 = createTestDir(tempDir.getRoot(), "d3");

    createSymLink(d11.getPath(), d1.getPath() + "/d11_link");
    createSymLink(d3.getPath(), d3.getPath() + "/d3_rec_link");

    myRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir.getRoot());
    assertNotNull(tempDir.getRoot().toString(), myRoot);
  }

  @After
  public void tearDown() {
    myRoot = null;
  }

  @Test
  public void visitAll() {
    doTest(
      null, null,
      "-> / [0]\n" +
      "  -> d1 [1]\n" +
      "    -> d11 [2]\n" +
      "      -> f11.1 [3]\n" +
      "      <- f11.1 [4]\n" +
      "      -> f11.2 [3]\n" +
      "      <- f11.2 [4]\n" +
      "    <- d11 [3]\n" +
      "    -> d11_link [2]\n" +
      "      -> f11.1 [3]\n" +
      "      <- f11.1 [4]\n" +
      "      -> f11.2 [3]\n" +
      "      <- f11.2 [4]\n" +
      "    <- d11_link [3]\n" +
      "    -> d12 [2]\n" +
      "    <- d12 [3]\n" +
      "    -> d13 [2]\n" +
      "      -> f13.1 [3]\n" +
      "      <- f13.1 [4]\n" +
      "      -> f13.2 [3]\n" +
      "      <- f13.2 [4]\n" +
      "    <- d13 [3]\n" +
      "    -> f1.1 [2]\n" +
      "    <- f1.1 [3]\n" +
      "  <- d1 [2]\n" +
      "  -> d2 [1]\n" +
      "    -> f2.1 [2]\n" +
      "    <- f2.1 [3]\n" +
      "    -> f2.2 [2]\n" +
      "    <- f2.2 [3]\n" +
      "  <- d2 [2]\n" +
      "  -> d3 [1]\n" +
      "    -> d3_rec_link [2]\n" +
      "    <- d3_rec_link [3]\n" +
      "  <- d3 [2]\n" +
      "<- / [1]\n");
  }

  @Test
  public void skipChildrenForDirectory() {
    doTest(
      file -> "d11".equals(file.getName()) ? VirtualFileVisitor.SKIP_CHILDREN : VirtualFileVisitor.CONTINUE,
      null,
      "-> / [0]\n" +
      "  -> d1 [1]\n" +
      "    -> d11 [2]\n" +
      "    -> d11_link [2]\n" +
      "      -> f11.1 [3]\n" +
      "      <- f11.1 [4]\n" +
      "      -> f11.2 [3]\n" +
      "      <- f11.2 [4]\n" +
      "    <- d11_link [3]\n" +
      "    -> d12 [2]\n" +
      "    <- d12 [3]\n" +
      "    -> d13 [2]\n" +
      "      -> f13.1 [3]\n" +
      "      <- f13.1 [4]\n" +
      "      -> f13.2 [3]\n" +
      "      <- f13.2 [4]\n" +
      "    <- d13 [3]\n" +
      "    -> f1.1 [2]\n" +
      "    <- f1.1 [3]\n" +
      "  <- d1 [2]\n" +
      "  -> d2 [1]\n" +
      "    -> f2.1 [2]\n" +
      "    <- f2.1 [3]\n" +
      "    -> f2.2 [2]\n" +
      "    <- f2.2 [3]\n" +
      "  <- d2 [2]\n" +
      "  -> d3 [1]\n" +
      "    -> d3_rec_link [2]\n" +
      "    <- d3_rec_link [3]\n" +
      "  <- d3 [2]\n" +
      "<- / [1]\n");
  }

  @Test
  public void skipChildrenForFiles() {
    doTest(
      file -> file.isDirectory() ? VirtualFileVisitor.CONTINUE : VirtualFileVisitor.SKIP_CHILDREN,
      null,
      "-> / [0]\n" +
      "  -> d1 [1]\n" +
      "    -> d11 [2]\n" +
      "      -> f11.1 [3]\n" +
      "      -> f11.2 [3]\n" +
      "    <- d11 [3]\n" +
      "    -> d11_link [2]\n" +
      "      -> f11.1 [3]\n" +
      "      -> f11.2 [3]\n" +
      "    <- d11_link [3]\n" +
      "    -> d12 [2]\n" +
      "    <- d12 [3]\n" +
      "    -> d13 [2]\n" +
      "      -> f13.1 [3]\n" +
      "      -> f13.2 [3]\n" +
      "    <- d13 [3]\n" +
      "    -> f1.1 [2]\n" +
      "  <- d1 [2]\n" +
      "  -> d2 [1]\n" +
      "    -> f2.1 [2]\n" +
      "    -> f2.2 [2]\n" +
      "  <- d2 [2]\n" +
      "  -> d3 [1]\n" +
      "    -> d3_rec_link [2]\n" +
      "    <- d3_rec_link [3]\n" +
      "  <- d3 [2]\n" +
      "<- / [1]\n");
  }

  @Test
  public void skipToParent() {
    Ref<VirtualFile> skip = Ref.create();
    doTest(
      file -> {
        if ("d1".equals(file.getName())) skip.set(file);
        return "f11.1".equals(file.getName()) ? skip.get() : VirtualFileVisitor.CONTINUE;
      },
      null,
      "-> / [0]\n" +
      "  -> d1 [1]\n" +
      "    -> d11 [2]\n" +
      "      -> f11.1 [3]\n" +
      "  -> d2 [1]\n" +
      "    -> f2.1 [2]\n" +
      "    <- f2.1 [3]\n" +
      "    -> f2.2 [2]\n" +
      "    <- f2.2 [3]\n" +
      "  <- d2 [2]\n" +
      "  -> d3 [1]\n" +
      "    -> d3_rec_link [2]\n" +
      "    <- d3_rec_link [3]\n" +
      "  <- d3 [2]\n" +
      "<- / [1]\n");
  }

  @Test
  public void skipToRoot() {
    doTest(
      file -> "f11.1".equals(file.getName()) ? myRoot : VirtualFileVisitor.CONTINUE,
      null,
      "-> / [0]\n" +
      "  -> d1 [1]\n" +
      "    -> d11 [2]\n" +
      "      -> f11.1 [3]\n");
  }

  @Test
  public void abort() {
    doTest(
      file -> {
        if ("f11.1".equals(file.getName())) throw new AbortException();
        return VirtualFileVisitor.CONTINUE;
      },
      null,
      "-> / [0]\n" +
      "  -> d1 [1]\n" +
      "    -> d11 [2]\n" +
      "      -> f11.1 [3]\n");
  }

  @Test
  public void depthLimit() {
    doTest(
      null, null,
      "-> / [0]\n" +
      "<- / [1]\n",
      VirtualFileVisitor.limit(0)
    );

    doTest(
      null, null,
      "-> / [0]\n" +
      "  -> d1 [1]\n" +
      "  <- d1 [2]\n" +
      "  -> d2 [1]\n" +
      "  <- d2 [2]\n" +
      "  -> d3 [1]\n" +
      "  <- d3 [2]\n" +
      "<- / [1]\n",
      VirtualFileVisitor.ONE_LEVEL_DEEP
    );

    doTest(
      null, null,
      "-> d1 [0]\n" +
      "<- d1 [1]\n" +
      "-> d2 [0]\n" +
      "<- d2 [1]\n" +
      "-> d3 [0]\n" +
      "<- d3 [1]\n",
      VirtualFileVisitor.SKIP_ROOT, VirtualFileVisitor.ONE_LEVEL_DEEP);
  }

  @Test
  public void customIterable() {
    doTest(
      null,
      file -> "d13".equals(file.getName()) ? Collections.singletonList(file.getChildren()[1]) : null,
      "-> / [0]\n" +
      "  -> d1 [1]\n" +
      "    -> d11 [2]\n" +
      "      -> f11.1 [3]\n" +
      "      <- f11.1 [4]\n" +
      "      -> f11.2 [3]\n" +
      "      <- f11.2 [4]\n" +
      "    <- d11 [3]\n" +
      "    -> d11_link [2]\n" +
      "      -> f11.1 [3]\n" +
      "      <- f11.1 [4]\n" +
      "      -> f11.2 [3]\n" +
      "      <- f11.2 [4]\n" +
      "    <- d11_link [3]\n" +
      "    -> d12 [2]\n" +
      "    <- d12 [3]\n" +
      "    -> d13 [2]\n" +
      "      -> f13.2 [3]\n" +
      "      <- f13.2 [4]\n" +
      "    <- d13 [3]\n" +
      "    -> f1.1 [2]\n" +
      "    <- f1.1 [3]\n" +
      "  <- d1 [2]\n" +
      "  -> d2 [1]\n" +
      "    -> f2.1 [2]\n" +
      "    <- f2.1 [3]\n" +
      "    -> f2.2 [2]\n" +
      "    <- f2.2 [3]\n" +
      "  <- d2 [2]\n" +
      "  -> d3 [1]\n" +
      "    -> d3_rec_link [2]\n" +
      "    <- d3_rec_link [3]\n" +
      "  <- d3 [2]\n" +
      "<- / [1]\n");
  }

  private static class AbortException extends RuntimeException { }

  private void doTest(@Nullable Function<? super VirtualFile, Object> condition,
                      @Nullable Function<? super VirtualFile, ? extends Iterable<VirtualFile>> iterable,
                      @NotNull String expected,
                      VirtualFileVisitor.Option @NotNull ... options) {
    MultiMap<VirtualFile, Pair<VirtualFile, String>> visitLog = MultiMap.create();
    Map<VirtualFile, String> backLog = new HashMap<>();
    try {
      Set<VirtualFile> visited = new HashSet<>();
      VfsUtilCore.visitChildrenRecursively(myRoot, new VirtualFileVisitor<Integer>(options) {
        { setValueForChildren(0); }

        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
          if (!visited.add(file)) {
            throw new AssertionError(file + " already visited");
          }
          return true;
        }

        @NotNull
        @Override
        public Result visitFileEx(@NotNull VirtualFile file) {
          if (!visited.add(file)) {
            throw new AssertionError(file + " already visited");
          }

          String name = file.equals(myRoot) ? "/" : file.getName();
          String s = "-> " + name + " [" + getCurrentValue() + "]\n";
          visitLog.putValue(file.getParent(), Pair.create(file, s));

          setValueForChildren(getCurrentValue() + 1);

          if (condition != null) {
            Object result = condition.fun(file);
            if (result instanceof Result) return (Result)result;
            if (result instanceof VirtualFile) return skipTo((VirtualFile)result);
          }
          return CONTINUE;
        }

        @Override
        public void afterChildrenVisited(@NotNull VirtualFile file) {
          String name = file.equals(myRoot) ? "/" : file.getName();
          String s = "<- " + name + " [" + getCurrentValue() + "]\n";
          backLog.put(file, s);
        }

        @Nullable
        @Override
        public Iterable<VirtualFile> getChildrenIterable(@NotNull VirtualFile file) {
          return iterable != null ? iterable.fun(file) : super.getChildrenIterable(file);
        }
      });
    }
    catch (AbortException ignore) { }

    StringBuilder out = new StringBuilder();
    toLog(ArrayUtil.contains(VirtualFileVisitor.SKIP_ROOT, options) ? myRoot : myRoot.getParent(), visitLog, backLog, 0, out);
    assertEquals(expected, out.toString());
  }

  private static void toLog(VirtualFile root,
                            MultiMap<VirtualFile, Pair<VirtualFile, String>> visitLog,
                            Map<VirtualFile, String> backLog, int level,
                            StringBuilder out) {
    List<Pair<VirtualFile, String>> visited = new ArrayList<>(visitLog.get(root));
    visited.sort(Comparator.comparing(o -> o.first.getName()));
    for (Pair<VirtualFile, String> pair : visited) {
      String log = pair.second;
      out.append(StringUtil.repeat("  ", level)).append(log);
      VirtualFile child = pair.first;
      toLog(child, visitLog, backLog, level+1, out);
      String back = backLog.get(child);
      if (back != null) {
        out.append(StringUtil.repeat("  ", level)).append(back);
      }
    }
  }
}
