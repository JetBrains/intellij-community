/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jetbrains.plugins.groovy.refactoring.copy;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.copy.CopyClassesHandler;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.Collections;

/**
 * @author peter
 */
public class GroovyCopyClassTest extends LightCodeInsightFixtureTestCase {

  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/copy/";
  }

  public void testBetweenPackages() throws Throwable {
    final String testName = getTestName(false);
    myFixture.copyFileToProject(testName + ".groovy", "foo/" + testName + ".groovy");
    myFixture.addClass("package foo; public class Bar {}");
    myFixture.addClass("package bar; public class Bar {}");

    final PsiClass srcClass = myFixture.getJavaFacade().findClass("foo." + testName, GlobalSearchScope.allScope(getProject()));
    assertTrue(CopyClassesHandler.canCopyClass(srcClass));
    new WriteCommandAction(getProject()) {
      protected void run(Result result) throws Throwable {
        CopyClassesHandler.doCopyClasses(Collections.singletonMap(srcClass.getNavigationElement().getContainingFile(), new PsiClass[]{srcClass}), testName + "_after", srcClass.getManager().findDirectory(myFixture.getTempDirFixture().getFile("bar")),
                                         getProject());
      }
    }.execute();

    myFixture.checkResultByFile("bar/" + testName + "_after.groovy", testName + "_after.groovy", true);
  }

  public void testCopyScript() throws Throwable {
    final String testName = getTestName(false);
    myFixture.copyFileToProject(testName + ".groovy", "/foo/" + testName + ".groovy");

    assertFalse(
      CopyClassesHandler.canCopyClass(myFixture.getJavaFacade().findClass("foo." + testName, GlobalSearchScope.allScope(getProject()))));
  }

}
