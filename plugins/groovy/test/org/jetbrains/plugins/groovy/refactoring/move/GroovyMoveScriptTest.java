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

package org.jetbrains.plugins.groovy.refactoring.move;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GroovyMoveScriptTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/move/moveScript/";
  }

  public void testMoveScriptBasic() throws Exception {
    doTest(new String[]{"a/Script.groovy"}, "b");
  }

  public void testUpdateReferences() throws Exception {
    doTest(new String[]{"a/Script.groovy"}, "b");
  }

  public void testMultiMove() throws Exception {
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
    final PsiClass[] classes = classList.toArray(new PsiClass[classList.size()]);
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
    getProject().getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();

    VirtualFileManager.getInstance().syncRefresh();
    try {
      PlatformTestUtil.assertDirectoriesEqual(expectedRoot, actualRoot);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }


}
