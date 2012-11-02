/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vfs;

import com.intellij.mock.MockVirtualFile;
import com.intellij.mock.MockVirtualLink;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformUltraLiteTestFixture;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class VirtualFileVisitorTest {
  private static PlatformUltraLiteTestFixture myFixture;
  private static VirtualFile myRoot;

  @BeforeClass
  public static void setUp() throws Exception {
    myFixture = PlatformUltraLiteTestFixture.getFixture();
    myFixture.setUp();
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
  public static void tearDown() throws Exception {
    myRoot = null;
    myFixture.tearDown();
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
      new Function<VirtualFile, Object>() {
        @Override
        public Object fun(VirtualFile file) {
          return "d11".equals(file.getName()) ? VirtualFileVisitor.SKIP_CHILDREN : VirtualFileVisitor.CONTINUE;
        }
      },
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
      new Function<VirtualFile, Object>() {
        @Override
        public Object fun(VirtualFile file) {
          return file.isDirectory() ? VirtualFileVisitor.CONTINUE : VirtualFileVisitor.SKIP_CHILDREN;
        }
      },
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
    final Ref<VirtualFile> skip = Ref.create();
    doTest(
      new Function<VirtualFile, Object>() {
        @Override
        public Object fun(VirtualFile file) {
          if ("d1".equals(file.getName())) skip.set(file);
          return "f11.1".equals(file.getName()) ? skip.get() : VirtualFileVisitor.CONTINUE;
        }
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
      new Function<VirtualFile, Object>() {
        @Override
        public Object fun(VirtualFile file) {
          return "f11.1".equals(file.getName()) ? myRoot : VirtualFileVisitor.CONTINUE;
        }
      },
      null,
      "-> / [0]\n" +
      "  -> d1 [1]\n" +
      "    -> d11 [2]\n" +
      "      -> f11.1 [3]\n");
  }

  @Test
  public void abort() {
    doTest(
      new Function<VirtualFile, Object>() {
        @Override
        public Object fun(VirtualFile file) {
          if ("f11.1".equals(file.getName())) {
            throw new AbortException();
          }
          return VirtualFileVisitor.CONTINUE;
        }
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
      new NullableFunction<VirtualFile, Iterable<VirtualFile>>() {
        @Override
        public Iterable<VirtualFile> fun(VirtualFile file) {
          return "d13".equals(file.getName()) ? Collections.singletonList(file.getChildren()[1]) : null;
        }
      },
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

  private static void doTest(@Nullable final Function<VirtualFile, Object> condition,
                             @Nullable final Function<VirtualFile, Iterable<VirtualFile>> iterable,
                             @NonNls @NotNull String expected,
                             @NotNull VirtualFileVisitor.Option... options) {
    final StringBuilder sb = new StringBuilder();

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

  private static MockVirtualFile dir(@NonNls @NotNull String name, MockVirtualFile... children) {
    final MockVirtualFile root = new MockVirtualFile(true, name);
    for (MockVirtualFile child : children) {
      root.addChild(child);
    }
    return root;
  }

  private static MockVirtualFile file(@NonNls @NotNull String name) {
    return new MockVirtualFile(name);
  }

  private static void link(@NonNls @NotNull String targetPath, @NotNull @NonNls String linkPath) {
    final VirtualFile target = myRoot.findFileByRelativePath(targetPath);
    assertNotNull(targetPath, target);
    final int pos = linkPath.lastIndexOf('/');
    final VirtualFile linkParent = myRoot.findFileByRelativePath(linkPath.substring(0, pos));
    assertNotNull(linkPath, linkParent);
    ((MockVirtualFile)linkParent).addChild(new MockVirtualLink(linkPath.substring(pos + 1), target));
  }
}
