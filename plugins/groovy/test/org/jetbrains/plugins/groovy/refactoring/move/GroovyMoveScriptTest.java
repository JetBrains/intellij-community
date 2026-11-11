// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.refactoring.move;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Maxim.Medvedev
 */
@TestApplication
public class GroovyMoveScriptTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "refactoring/move/moveScript/");
  }

  @Test
  public void testMoveScriptBasic() {
    doTest(new String[]{"a/Script.groovy"}, "b");
  }

  @Test
  public void testUpdateReferences() {
    doTest(new String[]{"a/Script.groovy"}, "b");
  }

  @Test
  public void testMultiMove() {
    doTest(new String[]{"a/Script.groovy", "a/Script2.groovy"}, "b");
  }

  @Test
  public void testScriptWithClasses() {
    doTest(new String[]{"a/Foo.groovy"}, "b");
  }

  @Test
  public void testFileWithTwoClasses() {
    doTest(new String[]{"a/Foo.groovy"}, "b");
  }

  @Test
  public void testMoveToSamePackage() {
    doTest(new String[]{"a/Foo.groovy"}, "b");
  }

  private void performAction(String[] fileNames, String newDirName, String dir) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiFile[] files = new PsiFile[fileNames.length];
    for (int i = 0; i < files.length; i++) {
      String fileName = fileNames[i];
      final VirtualFile file = myFixture.getTempDirFixture().getFile(dir + "/" + fileName);
      assertNotNull(file, "File " + fileName + " not found");

      files[i] = PsiManager.getInstance(myFixture.getProject()).findFile(file);
      assertNotNull(files[i], "File " + fileName + " not found");
    }
    final VirtualFile virDir = myFixture.getTempDirFixture().getFile(dir + "/" + newDirName);
    assertNotNull(virDir, "Directory " + newDirName + " not found");

    final PsiDirectory psiDirectory = PsiManager.getInstance(myFixture.getProject()).findDirectory(virDir);
    assertNotNull(psiDirectory, "Directory " + newDirName + " not found");

    final PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(psiDirectory);
    List<PsiClass> classList = new ArrayList<>();
    for (PsiFile file : files) {
      Collections.addAll(classList, ((PsiClassOwner)file).getClasses());
    }
    final PsiClass[] classes = classList.toArray(PsiClass.EMPTY_ARRAY);
    new MoveClassesOrPackagesProcessor(myFixture.getProject(), classes, new SingleSourceRootMoveDestination(PackageWrapper.create(pkg), psiDirectory), true, true, null).run();

    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  private void doTest(String[] fileNames, String newDirName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String testName = myFixture.getTestName(true);
    final VirtualFile actualRoot = myFixture.copyDirectoryToProject(testName + "/before", "");

    EdtTestUtil.runInEdtAndWait(() -> {
      performAction(fileNames, newDirName, VfsUtilCore.getRelativePath(actualRoot, myFixture.getTempDirFixture().getFile(""), '/'));

      final VirtualFile expectedRoot = StandardFileSystems.local().findFileByPath(myFixture.getTestDataPath() + myFixture.getTestName(true) + "/after");
      //File expectedRoot = new File(getTestDataPath() + testName + "/after");
      PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();

      VfsTestUtil.syncRefresh();
      try {
        PlatformTestUtil.assertDirectoriesEqual(expectedRoot, actualRoot);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

}
