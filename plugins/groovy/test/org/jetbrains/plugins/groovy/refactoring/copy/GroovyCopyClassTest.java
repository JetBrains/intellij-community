// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.copy;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.copy.CopyClassesHandler;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GroovyCopyClassTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "refactoring/copy/");
  }

  @Test
  public void testBetweenPackages() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String testName = myFixture.getTestName(false);
    myFixture.copyFileToProject(testName + ".groovy", "foo/" + testName + ".groovy");
    myFixture.addClass("package foo; public class Bar {}");
    myFixture.addClass("package bar; public class Bar {}");

    final PsiClass srcClass =
      ReadAction.computeBlocking(() -> myFixture.getJavaFacade().findClass("foo." + testName, GlobalSearchScope.allScope(myFixture.getProject())));
    assertTrue(ReadAction.computeBlocking(() -> CopyClassesHandler.canCopyClass(srcClass)));
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), (Computable<Collection<PsiFile>>)() -> {
        Map<PsiFile, PsiClass[]> map = Collections.singletonMap(srcClass.getNavigationElement().getContainingFile(), new PsiClass[]{srcClass});
        PsiDirectory dir = srcClass.getManager().findDirectory(myFixture.getTempDirFixture().getFile("bar"));
        return CopyClassesHandler.doCopyClasses(map, testName + "_after", dir, myFixture.getProject());
    });

    myFixture.checkResultByFile("bar/" + testName + "_after.groovy", testName + "_after.groovy", true);
  }

  @Test
  public void testCopyScript() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String testName = myFixture.getTestName(false);
    VirtualFile file = myFixture.copyFileToProject(testName + ".groovy", "foo/" + testName + ".groovy");
    ReadAction.runBlocking(() -> {
      PsiFile psiFile = myFixture.getPsiManager().findFile(file);
      //would be copied as file
      assertFalse(CopyClassesHandler.canCopyClass(myFixture.getJavaFacade().findClass("foo." + testName, GlobalSearchScope.allScope(myFixture.getProject()))));
      assertFalse(CopyClassesHandler.canCopyClass(psiFile));
    });
  }
}