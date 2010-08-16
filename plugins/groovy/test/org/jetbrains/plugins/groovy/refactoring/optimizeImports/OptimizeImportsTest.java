/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.optimizeImports;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.lang.editor.GroovyImportOptimizer;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author ilyas
 */
public class OptimizeImportsTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "optimizeImports/";
  }

  public void testNewline() throws Throwable {
    doTest();
  }

  public void testAliased() throws Throwable {
    doTest();
  }

  public void testSimpleOptimize() throws Throwable {
    doTest();
  }

  public void testCommented() throws Throwable {
    doTest();
  }

  public void testOptimizeExists() throws Throwable {
    doTest();
  }

  public void testOptimizeAlias() throws Throwable {
    doTest();
  }

  public void testFoldImports() throws Throwable {
    doTest();
  }

  public void testFoldImports2() throws Throwable {
    doTest();
  }

  public void testUntypedCall() throws Throwable {
    doTest();
  }

  public void testFoldImports3() throws Throwable {
    doTest();
  }

  public void testFoldImports4() throws Throwable {
    doTest();
  }

  public void testFoldImports5() throws Throwable {
    doTest();
  }

  public void testFixPoint() throws Throwable {
    doTest();
  }

  public void testUtilListMasked() throws Throwable {
    myFixture.addClass("package java.awt; public class List {}");
    doTest();
  }

  public void testExtraLineFeed() throws Throwable {
    myFixture.addClass("package groovy.io; public class EncodingAwareBufferedWriter {}");
    myFixture.addClass("package groovy.io; public class PlatformLineWriter {}");
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".groovy", "foo/bar.groovy"));
    doOptimizeImports();
    doOptimizeImports();
    myFixture.checkResultByFile(getTestName(false) + ".groovy");
  }

  public void testSemicolons() throws Throwable {
    doTest();
  }

  public void testSameFile() throws Throwable {
    doTest();
  }

  public void testSamePackage() throws Throwable {
    myFixture.addClass("package foo; public class Bar {}");
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".groovy", "foo/Foo.groovy"));
    doOptimizeImports();
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testQualifiedUsage() throws Throwable {
    myFixture.addClass("package foo; public class Bar {}");
    doTest();
  }

  public void testFileHeader() throws Throwable {
    doTest();
  }

  public void testRemoveImplicitlyImported() throws Throwable { doTest(); }
  public void testRemoveImplicitlyDemandImported() throws Throwable { doTest(); }
  public void testDontRemoveRedImports() throws Throwable { doTest(); }

  public void testRemoveSamePackaged() throws Throwable { 
    myFixture.addClass("package foo.bar; public class Aaaa {}");
    myFixture.addClass("package foo.bar; public class Bbbb {}");
    myFixture.addClass("package foo.bar; public class Zzzz {}");
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".groovy", "foo/Foo.groovy"));
    doOptimizeImports();
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testJustWrongImport() throws Exception {
    myFixture.configureByText("a.groovy", "import a.b.c.d");
    doOptimizeImports();
    myFixture.checkResult("import a.b.c.d");
  }

  private void doTest() throws Throwable {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).clone();
    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(settings);
    settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 3;
    try {
      myFixture.configureByFile(getTestName(false) + ".groovy");

      doOptimizeImports();
      PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
      ((DocumentEx)myFixture.getEditor().getDocument()).stripTrailingSpaces(false);
      myFixture.checkResultByFile(getTestName(false) + "_after.groovy");

    }
    finally {
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    }
  }

  private void doOptimizeImports() {
    GroovyImportOptimizer optimizer = new GroovyImportOptimizer();
    final Runnable runnable = optimizer.processFile(myFixture.getFile());

    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(runnable);
      }
    }, "Optimize imports", null);
  }

}
