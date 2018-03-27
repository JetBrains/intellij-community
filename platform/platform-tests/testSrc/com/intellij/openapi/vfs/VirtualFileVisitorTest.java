// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.mock.MockVirtualFile;
import com.intellij.mock.MockVirtualLink;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;

import static com.intellij.mock.MockVirtualFile.dir;
import static com.intellij.mock.MockVirtualFile.file;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class VirtualFileVisitorTest extends BareTestFixtureTestCase {
  private static VirtualFile myRoot;

  @BeforeClass
  public static void setUp() {
    myRoot =
      dir("/",
          dir("d1",
              dir("d11",
                  file("f11.1"),
                  file("f11.2")),
              file("f1.1"),
              dir("d12"),
              dir("d13",
                  file("f13.1"),
                  file("f13.2"))),
          dir("d2",
              file("f2.1"),
              file("f2.2")),
          dir("d3"));
    link("/d1/d11", "/d1/d11_link");
    link("/d3", "/d3/d3_rec_link");
  }

  @AfterClass
  public static void tearDown() {
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
      "    -> f1.1 [2]\n" +
      "    <- f1.1 [3]\n" +
      "    -> d12 [2]\n" +
      "    <- d12 [3]\n" +
      "    -> d13 [2]\n" +
      "      -> f13.1 [3]\n" +
      "      <- f13.1 [4]\n" +
      "      -> f13.2 [3]\n" +
      "      <- f13.2 [4]\n" +
      "    <- d13 [3]\n" +
      "    -> d11_link [2]\n" +
      "      -> f11.1 [3]\n" +
      "      <- f11.1 [4]\n" +
      "      -> f11.2 [3]\n" +
      "      <- f11.2 [4]\n" +
      "    <- d11_link [3]\n" +
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
      "      -> f1.1 [2]\n" +
      "      <- f1.1 [3]\n" +
      "      -> d12 [2]\n" +
      "      <- d12 [3]\n" +
      "      -> d13 [2]\n" +
      "        -> f13.1 [3]\n" +
      "        <- f13.1 [4]\n" +
      "        -> f13.2 [3]\n" +
      "        <- f13.2 [4]\n" +
      "      <- d13 [3]\n" +
      "      -> d11_link [2]\n" +
      "        -> f11.1 [3]\n" +
      "        <- f11.1 [4]\n" +
      "        -> f11.2 [3]\n" +
      "        <- f11.2 [4]\n" +
      "      <- d11_link [3]\n" +
      "    <- d1 [2]\n" +
      "    -> d2 [1]\n" +
      "      -> f2.1 [2]\n" +
      "      <- f2.1 [3]\n" +
      "      -> f2.2 [2]\n" +
      "      <- f2.2 [3]\n" +
      "    <- d2 [2]\n" +
      "    -> d3 [1]\n" +
      "      -> d3_rec_link [2]\n" +
      "      <- d3_rec_link [3]\n" +
      "    <- d3 [2]\n" +
      "  <- / [1]\n");
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
      "        -> f11.2 [3]\n" +
      "        <- d11 [3]\n" +
      "        -> f1.1 [2]\n" +
      "          -> d12 [2]\n" +
      "          <- d12 [3]\n" +
      "          -> d13 [2]\n" +
      "            -> f13.1 [3]\n" +
      "              -> f13.2 [3]\n" +
      "              <- d13 [3]\n" +
      "              -> d11_link [2]\n" +
      "                -> f11.1 [3]\n" +
      "                  -> f11.2 [3]\n" +
      "                  <- d11_link [3]\n" +
      "                <- d1 [2]\n" +
      "                -> d2 [1]\n" +
      "                  -> f2.1 [2]\n" +
      "                    -> f2.2 [2]\n" +
      "                    <- d2 [2]\n" +
      "                    -> d3 [1]\n" +
      "                      -> d3_rec_link [2]\n" +
      "                      <- d3_rec_link [3]\n" +
      "                    <- d3 [2]\n" +
      "                  <- / [1]\n");
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
      "        -> d2 [1]\n" +
      "          -> f2.1 [2]\n" +
      "          <- f2.1 [3]\n" +
      "          -> f2.2 [2]\n" +
      "          <- f2.2 [3]\n" +
      "        <- d2 [2]\n" +
      "        -> d3 [1]\n" +
      "          -> d3_rec_link [2]\n" +
      "          <- d3_rec_link [3]\n" +
      "        <- d3 [2]\n" +
      "      <- / [1]\n");
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
      "    -> f1.1 [2]\n" +
      "    <- f1.1 [3]\n" +
      "    -> d12 [2]\n" +
      "    <- d12 [3]\n" +
      "    -> d13 [2]\n" +
      "      -> f13.2 [3]\n" +
      "      <- f13.2 [4]\n" +
      "    <- d13 [3]\n" +
      "    -> d11_link [2]\n" +
      "      -> f11.1 [3]\n" +
      "      <- f11.1 [4]\n" +
      "      -> f11.2 [3]\n" +
      "      <- f11.2 [4]\n" +
      "    <- d11_link [3]\n" +
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

  private static void doTest(@Nullable Function<VirtualFile, Object> condition,
                             @Nullable Function<VirtualFile, Iterable<VirtualFile>> iterable,
                             @NotNull String expected,
                             @NotNull VirtualFileVisitor.Option... options) {
    StringBuilder sb = new StringBuilder();

    try {
      VfsUtilCore.visitChildrenRecursively(myRoot, new VirtualFileVisitor<Integer>(options) {
        { setValueForChildren(0); }

        private int level = 0;

        @NotNull
        @Override
        public Result visitFileEx(@NotNull VirtualFile file) {
          sb.append(StringUtil.repeat("  ", level++))
            .append("-> ").append(file.getName()).append(" [").append(getCurrentValue()).append("]\n");

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
          sb.append(StringUtil.repeat("  ", --level))
            .append("<- ").append(file.getName()).append(" [").append(getCurrentValue()).append("]\n");
        }

        @Nullable
        @Override
        public Iterable<VirtualFile> getChildrenIterable(@NotNull VirtualFile file) {
          return iterable != null ? iterable.fun(file) : super.getChildrenIterable(file);
        }
      });
    }
    catch (AbortException ignore) { }

    assertEquals(expected, sb.toString());
  }

  private static void link(@NotNull String targetPath, @NotNull String linkPath) {
    VirtualFile target = myRoot.findFileByRelativePath(targetPath);
    assertNotNull(targetPath, target);
    int pos = linkPath.lastIndexOf('/');
    VirtualFile linkParent = myRoot.findFileByRelativePath(linkPath.substring(0, pos));
    assertNotNull(linkPath, linkParent);
    ((MockVirtualFile)linkParent).addChild(new MockVirtualLink(linkPath.substring(pos + 1), target));
  }
}