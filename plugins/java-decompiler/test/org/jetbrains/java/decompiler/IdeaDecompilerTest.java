/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler;

import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class IdeaDecompilerTest extends LightCodeInsightFixtureTestCase {
  public void testSimple() {
    String path = PlatformTestUtil.getRtJarPath() + "!/java/lang/String.class";
    VirtualFile file = StandardFileSystems.jar().findFileByPath(path);
    assertNotNull(path, file);

    String decompiled = new IdeaDecompiler().getText(file).toString();
    assertTrue(decompiled, decompiled.startsWith(IdeaDecompiler.BANNER + "package java.lang;\n"));
    assertTrue(decompiled, decompiled.contains("public final class String"));
    assertTrue(decompiled, decompiled.contains("@deprecated"));
    assertTrue(decompiled, decompiled.contains("private static class CaseInsensitiveComparator"));
    assertFalse(decompiled, decompiled.contains("{ /* compiled code */ }"));
    assertFalse(decompiled, decompiled.contains("synthetic"));
  }

  public void testStubCompatibility() {
    String path = PlatformTestUtil.getRtJarPath() + "!/java";
    VirtualFile dir = StandardFileSystems.jar().findFileByPath(path);
    assertNotNull(path, dir);
    doTestStubCompatibility(dir);
  }

  private void doTestStubCompatibility(VirtualFile root) {
    VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (file.isDirectory()) {
          System.out.println(file.getPath());
        }
        else if (file.getFileType() == StdFileTypes.CLASS && !file.getName().contains("$") && !skip(file)) {
          PsiFile clsFile = getPsiManager().findFile(file);
          assertNotNull(file.getPath(), clsFile);
          PsiElement mirror = ((ClsFileImpl)clsFile).getMirror();
          String decompiled = mirror.getText();
          assertTrue(file.getPath(), decompiled.contains(file.getNameWithoutExtension()));
        }
        return true;
      }

      private boolean skip(VirtualFile file) {
        if (!SystemInfo.isJavaVersionAtLeast("1.8")) return false;
        String path = file.getPath();
        int p = path.indexOf("!/");
        return p > 0 && knowProblems.contains(path.substring(p + 2));
      }

      // todo[r.sh] drop when IDEA-129734 get fixed
      private final Set<String> knowProblems = ContainerUtil.newHashSet(
        "java/lang/reflect/AnnotatedElement.class", "java/util/stream/Nodes.class", "java/util/stream/FindOps.class",
        "java/util/stream/Collectors.class", "java/util/stream/DistinctOps.class", "java/util/stream/IntPipeline.class",
        "java/util/stream/LongPipeline.class", "java/util/stream/DoublePipeline.class"
      );
    });
  }

  public void testNavigation() {
    String path = PluginPathManager.getPluginHomePath("java-decompiler") + "/testData/Navigation.class";
    VirtualFile file = StandardFileSystems.local().findFileByPath(path);
    assertNotNull(path, file);
    myFixture.openFileInEditor(file);

    doTestNavigation(11, 14, 14, 10);  // to "m2()"
    doTestNavigation(15, 21, 14, 17);  // to "int i"
    doTestNavigation(16, 28, 15, 13);  // to "int r"
  }

  private void doTestNavigation(int line, int column, int expectedLine, int expectedColumn) {
    PsiElement target = GotoDeclarationAction.findTargetElement(getProject(), myFixture.getEditor(), offset(line, column));
    assertTrue(String.valueOf(target), target instanceof Navigatable);
    ((Navigatable)target).navigate(true);
    int expected = offset(expectedLine, expectedColumn);
    assertEquals(expected, myFixture.getCaretOffset());
  }

  private int offset(int line, int column) {
    return myFixture.getEditor().getDocument().getLineStartOffset(line - 1) + column - 1;
  }

  public void testHighlighting() {
    String path = PluginPathManager.getPluginHomePath("java-decompiler") + "/testData/Navigation.class";
    VirtualFile file = StandardFileSystems.local().findFileByPath(path);
    assertNotNull(path, file);
    myFixture.openFileInEditor(file);

    IdentifierHighlighterPassFactory.doWithHighlightingEnabled(new Runnable() {
      public void run() {
        myFixture.getEditor().getCaretModel().moveToOffset(offset(11, 14));  // m2(): usage, declaration
        assertEquals(2, myFixture.doHighlighting().size());
        myFixture.getEditor().getCaretModel().moveToOffset(offset(15, 21));  // int i: usage, declaration
        assertEquals(2, myFixture.doHighlighting().size());
        myFixture.getEditor().getCaretModel().moveToOffset(offset(16, 28));  // int r: usage, declaration
        assertEquals(2, myFixture.doHighlighting().size());
      }
    });
  }
}
