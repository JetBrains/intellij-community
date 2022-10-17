// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.copy

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.copy.CopyClassesHandler
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

class GroovyCopyClassTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return "${TestUtils.testDataPath}refactoring/copy/"
  }

  void testBetweenPackages() throws Throwable {
    final String testName = getTestName(false)
    myFixture.copyFileToProject("${testName}.groovy", "foo/${testName}.groovy")
    myFixture.addClass("package foo; public class Bar {}")
    myFixture.addClass("package bar; public class Bar {}")

    final PsiClass srcClass = myFixture.javaFacade.findClass("foo.$testName", GlobalSearchScope.allScope(project))
    assertTrue(CopyClassesHandler.canCopyClass(srcClass))
    WriteCommandAction.runWriteCommandAction(project, {
        def map = Collections.singletonMap(srcClass.navigationElement.containingFile, [srcClass] as PsiClass[])
        def dir = srcClass.manager.findDirectory(myFixture.tempDirFixture.getFile("bar"))
        CopyClassesHandler.doCopyClasses(map, "${testName}_after", dir, project)
      })

    myFixture.checkResultByFile("bar/${testName}_after.groovy", "${testName}_after.groovy", true)
  }

  void testCopyScript() throws Throwable {
    final String testName = getTestName(false)
    def file = myFixture.copyFileToProject("${testName}.groovy", "foo/${testName}.groovy")
    def psiFile = myFixture.psiManager.findFile(file)
    //would be copied as file
    assertFalse(CopyClassesHandler.canCopyClass(myFixture.javaFacade.findClass("foo.$testName", GlobalSearchScope.allScope(project))))
    assertFalse(CopyClassesHandler.canCopyClass(psiFile))
  }
}