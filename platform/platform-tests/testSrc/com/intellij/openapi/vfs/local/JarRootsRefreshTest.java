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
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;

public class JarRootsRefreshTest extends PlatformTestCase {
  public void testJarRefreshOnRenameOrMove() {
    File jar = IoTestUtil.createTestJar();
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar);
    assertNotNull(vFile);
    new WriteCommandAction.Simple(myProject) {
      @Override
      protected void run() {
        PsiTestUtil.addContentRoot(myModule, vFile.getParent());
      }
    }.execute();

    VirtualFile jarRoot = JarFileSystem.getInstance().getRootByLocal(vFile);
    assertNotNull(jarRoot);
    PsiFile file = getPsiManager().findFile(vFile);
    String newName = vFile.getName() + ".jar";
    rename(file, newName);

    assertFalse(jarRoot.isValid());

    checkMove(jar, vFile, file);
  }

  private void checkMove(File jar, VirtualFile vFile, PsiFile file) {
    VirtualFile jarRoot;
    File libDir = new File(jar.getParent(), "lib");
    assertTrue(libDir.mkdir());
    VirtualFile vLibDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(libDir);
    assertNotNull(vLibDir);

    jarRoot = JarFileSystem.getInstance().getRootByLocal(vFile);
    assertNotNull(jarRoot);
    assertTrue(jarRoot.isValid());
    PsiDirectory directory = getPsiManager().findDirectory(vLibDir);
    DataContext psiDataContext = SimpleDataContext.getSimpleContext(LangDataKeys.TARGET_PSI_ELEMENT.getName(), directory);
    new MoveHandler().invoke(myProject, new PsiElement[] {file}, psiDataContext);
    assertFalse(jarRoot.isValid());

    jarRoot = JarFileSystem.getInstance().getRootByLocal(vFile);
    assertNotNull(jarRoot);
    assertTrue(jarRoot.isValid());
    rename(directory, "lib2");
    assertFalse(jarRoot.isValid());
  }

  private static void rename(PsiNamedElement file, String newName) {
    DataContext psiDataContext = SimpleDataContext.getSimpleContext(CommonDataKeys.PSI_ELEMENT.getName(), file);
    RenameHandler renameHandler = RenameHandlerRegistry.getInstance().getRenameHandler(psiDataContext);
    assertNotNull(renameHandler);
    PsiElementRenameHandler.rename(file, file.getProject(), file, null, newName);
  }
}