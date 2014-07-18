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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class IdeaDecompilerTest extends LightCodeInsightFixtureTestCase {
  public void testSimple() {
    String path = PlatformTestUtil.getRtJarPath() + "!/java/lang/String.class";
    VirtualFile file = StandardFileSystems.jar().findFileByPath(path);
    assertNotNull(path, file);

    String decompiled = new IdeaDecompiler().getText(file).toString();
    assertTrue(decompiled, decompiled.contains("public final class String"));
    assertTrue(decompiled, decompiled.contains("@deprecated"));
    assertTrue(decompiled, decompiled.contains("private static class CaseInsensitiveComparator"));
    assertFalse(decompiled, decompiled.contains("{ /* compiled code */ }"));
    assertFalse(decompiled, decompiled.contains("synthetic"));
  }

  public void testEnum() { doTestDecompiler(); }
  public void testDeprecations() { doTestDecompiler(); }
  public void testExtendsList() { doTestDecompiler(); }
  public void testParameters() { doTestDecompiler(); }
  public void testConstants() { doTestDecompiler(); }
  public void testAnonymous() { doTestDecompiler(); }
  public void testCodeConstructs() { doTestDecompiler(); }

  private void doTestDecompiler() {
    String name = PluginPathManager.getPluginHomePath("java-decompiler") + "/testData/" + getName().substring(4);
    String path = name + ".class";
    VirtualFile file = StandardFileSystems.local().findFileByPath(path);
    assertNotNull(path, file);
    file.getParent().getChildren();
    file.getParent().refresh(false, true);

    try {
      CharSequence text = new IdeaDecompiler().getText(file);
      assertNotNull(text);
      String expected = FileUtil.loadFile(new File(name + ".txt"), "UTF-8");
      assertEquals(StringUtil.convertLineSeparators(expected), text.toString());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void testStubCompatibilityRt() {
    String path = PlatformTestUtil.getRtJarPath() + "!/";
    VirtualFile dir = StandardFileSystems.jar().findFileByPath(path);
    assertNotNull(path, dir);
    doTestStubCompatibility(dir);
  }

  public void testStubCompatibilityIdea() {
    String path = PathManager.getHomePath() + "/out/production";
    if (!new File(path).exists()) path = PathManager.getHomePath() + "/out/classes/production";
    VirtualFile dir = StandardFileSystems.local().refreshAndFindFileByPath(path);
    assertNotNull(path, dir);
    doTestStubCompatibility(dir);
  }

  private void doTestStubCompatibility(VirtualFile root) {
    doTestStubCompatibility(root, null);
  }

  private void doTestStubCompatibility(VirtualFile root, @Nullable final String textPath) {
    final int pathStart = root.getPath().length();
    final boolean compare = textPath != null && new File(textPath).exists();

    VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (!file.isDirectory() && file.getFileType() == StdFileTypes.CLASS && !file.getName().contains("$")) {
          PsiFile clsFile = getPsiManager().findFile(file);
          assertNotNull(file.getPath(), clsFile);

          PsiElement mirror = ((ClsFileImpl)clsFile).getMirror().copy();
          if (textPath != null) {
            collapseCodeBlocks(mirror);
          }
          String decompiled = mirror.getText();
          assertTrue(file.getPath(), decompiled.contains(file.getNameWithoutExtension()));

          if (textPath != null) {
            try {
              File txtFile = new File(textPath, file.getPath().substring(pathStart));
              if (!compare) {
                FileUtil.writeToFile(txtFile, decompiled.getBytes("UTF-8"));
              }
              else {
                String expected = FileUtil.loadFile(txtFile, "UTF-8");
                assertEquals(file.getPath(), expected, decompiled);
              }
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        }
        return true;
      }
    });
  }

  private static void collapseCodeBlocks(PsiElement original) {
    final PsiElementFactory factory = PsiElementFactory.SERVICE.getInstance(original.getProject());
    original.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitMethod(PsiMethod method) {
        PsiCodeBlock body = method.getBody();
        if (body != null) {
          body.replace(factory.createCodeBlockFromText("{ /* collapsed */}", null));
        }
      }

      @Override
      public void visitClass(PsiClass aClass) {
        for (PsiClassInitializer initializer : aClass.getInitializers()) {
          PsiCodeBlock body = initializer.getBody();
          body.replace(factory.createCodeBlockFromText("{ /* collapsed */}", null));
        }
        super.visitClass(aClass);
      }
    });
  }
}
