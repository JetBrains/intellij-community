// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.local;

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
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;
import java.io.IOException;

public class JarRootsRefreshTest extends HeavyPlatformTestCase {
  public void testJarRefreshOnRenameOrMove() throws IOException {
    File jar = IoTestUtil.createTestJar(createTempFile("test.jar", ""));
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar);
    assertNotNull(vFile);
    WriteCommandAction.writeCommandAction(myProject).run(() -> PsiTestUtil.addContentRoot(myModule, vFile.getParent()));

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
    DataContext psiDataContext = SimpleDataContext.getSimpleContext(LangDataKeys.TARGET_PSI_ELEMENT, directory);
    new MoveHandler().invoke(myProject, new PsiElement[] {file}, psiDataContext);
    assertFalse(jarRoot.isValid());

    jarRoot = JarFileSystem.getInstance().getRootByLocal(vFile);
    assertNotNull(jarRoot);
    assertTrue(jarRoot.isValid());
    rename(directory, "lib2");
    assertFalse(jarRoot.isValid());
  }

  private void rename(PsiNamedElement file, String newName) {
    new RenameProcessor(getProject(), file, newName, false, false).run();
  }
}