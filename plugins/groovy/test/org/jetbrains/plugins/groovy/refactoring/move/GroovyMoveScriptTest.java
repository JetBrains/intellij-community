// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.refactoring.move;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GroovyMoveScriptTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/move/moveScript/";
  }

  public void testMoveScriptBasic() {
    doTest(new String[]{"a/Script.groovy"}, "b");
  }

  public void testUpdateReferences() {
    doTest(new String[]{"a/Script.groovy"}, "b");
  }

  public void testMultiMove() {
    doTest(new String[]{"a/Script.groovy", "a/Script2.groovy"}, "b");
  }

  public void testScriptWithClasses() {
    doTest(new String[]{"a/Foo.groovy"}, "b");
  }

  public void testFileWithTwoClasses() {
    doTest(new String[]{"a/Foo.groovy"}, "b");
  }

  public void testMoveToSamePackage() {
    doTest(new String[]{"a/Foo.groovy"}, "b");
  }

  private void performAction(String[] fileNames, String newDirName, String dir) {
    final PsiFile[] files = new PsiFile[fileNames.length];
    for (int i = 0; i < files.length; i++) {
      String fileName = fileNames[i];
      final VirtualFile file = myFixture.getTempDirFixture().getFile(dir + "/" + fileName);
      assertNotNull("File " + fileName + " not found", file);

      files[i] = PsiManager.getInstance(getProject()).findFile(file);
      assertNotNull("File " + fileName + " not found", files[i]);
    }
    final VirtualFile virDir = myFixture.getTempDirFixture().getFile(dir + "/" + newDirName);
    assertNotNull("Directory " + newDirName + " not found", virDir);

    final PsiDirectory psiDirectory = PsiManager.getInstance(getProject()).findDirectory(virDir);
    assertNotNull("Directory " + newDirName + " not found", psiDirectory);

    final PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(psiDirectory);
    List<PsiClass> classList = new ArrayList<>();
    for (PsiFile file : files) {
      Collections.addAll(classList, ((PsiClassOwner)file).getClasses());
    }
    final PsiClass[] classes = classList.toArray(PsiClass.EMPTY_ARRAY);
    new MoveClassesOrPackagesProcessor(getProject(), classes, new SingleSourceRootMoveDestination(PackageWrapper.create(pkg), psiDirectory), true, true, null).run();

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  private void doTest(String[] fileNames, String newDirName) {
    String testName = getTestName(true);
    final VirtualFile actualRoot = myFixture.copyDirectoryToProject(testName + "/before", "");

    performAction(fileNames, newDirName, VfsUtilCore.getRelativePath(actualRoot, myFixture.getTempDirFixture().getFile(""), '/'));

    final VirtualFile expectedRoot = LocalFileSystem.getInstance().findFileByPath(getTestDataPath() + getTestName(true) + "/after");
    //File expectedRoot = new File(getTestDataPath() + testName + "/after");
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();

    VirtualFileManager.getInstance().syncRefresh();
    try {
      PlatformTestUtil.assertDirectoriesEqual(expectedRoot, actualRoot);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }


}
