// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.copy;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.copy.CopyClassesHandler;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class GroovyCopyClassTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/copy/";
  }

  public void testBetweenPackages() {
    final String testName = getTestName(false);
    myFixture.copyFileToProject(testName + ".groovy", "foo/" + testName + ".groovy");
    myFixture.addClass("package foo; public class Bar {}");
    myFixture.addClass("package bar; public class Bar {}");

    final PsiClass srcClass = myFixture.getJavaFacade().findClass("foo." + testName, GlobalSearchScope.allScope(getProject()));
    assertTrue(CopyClassesHandler.canCopyClass(srcClass));
    WriteCommandAction.runWriteCommandAction(getProject(), (Computable<Collection<PsiFile>>)() -> {
        Map<PsiFile, PsiClass[]> map = Collections.singletonMap(srcClass.getNavigationElement().getContainingFile(), new PsiClass[]{srcClass});
        PsiDirectory dir = srcClass.getManager().findDirectory(myFixture.getTempDirFixture().getFile("bar"));
        return CopyClassesHandler.doCopyClasses(map, testName + "_after", dir, getProject());
    });

    myFixture.checkResultByFile("bar/" + testName + "_after.groovy", testName + "_after.groovy", true);
  }

  public void testCopyScript() {
    final String testName = getTestName(false);
    VirtualFile file = myFixture.copyFileToProject(testName + ".groovy", "foo/" + testName + ".groovy");
    PsiFile psiFile = myFixture.getPsiManager().findFile(file);
    //would be copied as file
    assertFalse(CopyClassesHandler.canCopyClass(myFixture.getJavaFacade().findClass("foo." + testName, GlobalSearchScope.allScope(getProject()))));
    assertFalse(CopyClassesHandler.canCopyClass(psiFile));
  }
}