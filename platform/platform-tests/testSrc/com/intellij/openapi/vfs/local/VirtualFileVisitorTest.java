// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.text.StringUtil;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.Assert.assertEquals;

public class VirtualFileVisitorTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory tempDir = new TempDirectory();

  private VirtualFile myRoot;

  @Before
  public void setUp() throws IOException {
    IoTestUtil.assumeSymLinkCreationIsSupported();

    tempDir.newFile("d1/f1.1");
    tempDir.newFile("d1/d11/f11.1");
    tempDir.newFile("d1/d11/f11.2");
    tempDir.newDirectory("d1/d12");
    tempDir.newFile("d1/d13/f13.1");
    tempDir.newFile("d1/d13/f13.2");
    tempDir.newFile("d2/f2.1");
    tempDir.newFile("d2/f2.2");
    tempDir.newDirectory("d3");

    Files.createSymbolicLink(tempDir.getRootPath().resolve("d1/d11_link"), Path.of("d11"));
    Files.createSymbolicLink(tempDir.getRootPath().resolve("d3/d3_rec_link"), Path.of(".."));

    myRoot = tempDir.getVirtualFileRoot();
  }

  @After
  public void tearDown() {
    myRoot = null;
  }

  @Test
  public void visitAll() {
    doTest(
      null, null,
      """
        -> / [0]
          -> d1 [1]
            -> d11 [2]
              -> f11.1 [3]
              <- f11.1 [4]
              -> f11.2 [3]
              <- f11.2 [4]
            <- d11 [3]
            -> d11_link [2]
              -> f11.1 [3]
              <- f11.1 [4]
              -> f11.2 [3]
              <- f11.2 [4]
            <- d11_link [3]
            -> d12 [2]
            <- d12 [3]
            -> d13 [2]
              -> f13.1 [3]
              <- f13.1 [4]
              -> f13.2 [3]
              <- f13.2 [4]
            <- d13 [3]
            -> f1.1 [2]
            <- f1.1 [3]
          <- d1 [2]
          -> d2 [1]
            -> f2.1 [2]
            <- f2.1 [3]
            -> f2.2 [2]
            <- f2.2 [3]
          <- d2 [2]
          -> d3 [1]
            -> d3_rec_link [2]
            <- d3_rec_link [3]
          <- d3 [2]
        <- / [1]
        """);
  }

  @Test
  public void skipChildrenForDirectory() {
    doTest(
      file -> "d11".equals(file.getName()) ? VirtualFileVisitor.SKIP_CHILDREN : VirtualFileVisitor.CONTINUE,
      null,
      """
        -> / [0]
          -> d1 [1]
            -> d11 [2]
            -> d11_link [2]
              -> f11.1 [3]
              <- f11.1 [4]
              -> f11.2 [3]
              <- f11.2 [4]
            <- d11_link [3]
            -> d12 [2]
            <- d12 [3]
            -> d13 [2]
              -> f13.1 [3]
              <- f13.1 [4]
              -> f13.2 [3]
              <- f13.2 [4]
            <- d13 [3]
            -> f1.1 [2]
            <- f1.1 [3]
          <- d1 [2]
          -> d2 [1]
            -> f2.1 [2]
            <- f2.1 [3]
            -> f2.2 [2]
            <- f2.2 [3]
          <- d2 [2]
          -> d3 [1]
            -> d3_rec_link [2]
            <- d3_rec_link [3]
          <- d3 [2]
        <- / [1]
        """);
  }

  @Test
  public void skipChildrenForFiles() {
    doTest(
      file -> file.isDirectory() ? VirtualFileVisitor.CONTINUE : VirtualFileVisitor.SKIP_CHILDREN,
      null,
      """
        -> / [0]
          -> d1 [1]
            -> d11 [2]
              -> f11.1 [3]
              -> f11.2 [3]
            <- d11 [3]
            -> d11_link [2]
              -> f11.1 [3]
              -> f11.2 [3]
            <- d11_link [3]
            -> d12 [2]
            <- d12 [3]
            -> d13 [2]
              -> f13.1 [3]
              -> f13.2 [3]
            <- d13 [3]
            -> f1.1 [2]
          <- d1 [2]
          -> d2 [1]
            -> f2.1 [2]
            -> f2.2 [2]
          <- d2 [2]
          -> d3 [1]
            -> d3_rec_link [2]
            <- d3_rec_link [3]
          <- d3 [2]
        <- / [1]
        """);
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
      """
        -> / [0]
          -> d1 [1]
            -> d11 [2]
              -> f11.1 [3]
          -> d2 [1]
            -> f2.1 [2]
            <- f2.1 [3]
            -> f2.2 [2]
            <- f2.2 [3]
          <- d2 [2]
          -> d3 [1]
            -> d3_rec_link [2]
            <- d3_rec_link [3]
          <- d3 [2]
        <- / [1]
        """);
  }

  @Test
  public void skipToRoot() {
    doTest(
      file -> "f11.1".equals(file.getName()) ? myRoot : VirtualFileVisitor.CONTINUE,
      null,
      """
        -> / [0]
          -> d1 [1]
            -> d11 [2]
              -> f11.1 [3]
        """);
  }

  @Test
  public void abort() {
    doTest(
      file -> {
        if ("f11.1".equals(file.getName())) throw new AbortException();
        return VirtualFileVisitor.CONTINUE;
      },
      null,
      """
        -> / [0]
          -> d1 [1]
            -> d11 [2]
              -> f11.1 [3]
        """);
  }

  @Test
  public void depthLimit() {
    doTest(
      null, null,
      """
        -> / [0]
        <- / [1]
        """,
      VirtualFileVisitor.limit(0)
    );

    doTest(
      null, null,
      """
        -> / [0]
          -> d1 [1]
          <- d1 [2]
          -> d2 [1]
          <- d2 [2]
          -> d3 [1]
          <- d3 [2]
        <- / [1]
        """,
      VirtualFileVisitor.ONE_LEVEL_DEEP
    );

    doTest(
      null, null,
      """
        -> d1 [0]
        <- d1 [1]
        -> d2 [0]
        <- d2 [1]
        -> d3 [0]
        <- d3 [1]
        """,
      VirtualFileVisitor.SKIP_ROOT, VirtualFileVisitor.ONE_LEVEL_DEEP);
  }

  @Test
  public void customIterable() {
    doTest(
      null,
      file -> "d13".equals(file.getName()) ? Collections.singletonList(file.getChildren()[1]) : null,
      """
        -> / [0]
          -> d1 [1]
            -> d11 [2]
              -> f11.1 [3]
              <- f11.1 [4]
              -> f11.2 [3]
              <- f11.2 [4]
            <- d11 [3]
            -> d11_link [2]
              -> f11.1 [3]
              <- f11.1 [4]
              -> f11.2 [3]
              <- f11.2 [4]
            <- d11_link [3]
            -> d12 [2]
            <- d12 [3]
            -> d13 [2]
              -> f13.2 [3]
              <- f13.2 [4]
            <- d13 [3]
            -> f1.1 [2]
            <- f1.1 [3]
          <- d1 [2]
          -> d2 [1]
            -> f2.1 [2]
            <- f2.1 [3]
            -> f2.2 [2]
            <- f2.2 [3]
          <- d2 [2]
          -> d3 [1]
            -> d3_rec_link [2]
            <- d3_rec_link [3]
          <- d3 [2]
        <- / [1]
        """);
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

        @Override
        public @NotNull Result visitFileEx(@NotNull VirtualFile file) {
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

        @Override
        public @Nullable Iterable<VirtualFile> getChildrenIterable(@NotNull VirtualFile file) {
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
