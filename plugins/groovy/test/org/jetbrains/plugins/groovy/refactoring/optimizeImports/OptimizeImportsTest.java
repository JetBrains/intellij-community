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
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.editor.GroovyImportOptimizer;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author ilyas
 */
public class OptimizeImportsTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addJdk(TestUtils.getMockJdkHome());
  }

  @Override
  protected String getBasePath() {
    return "/svnPlugins/groovy/testdata/optimizeImports/" + getTestName(true);
  }

  public void testNewline() throws Throwable {
    doTest("A.groovy");
  }

  public void testAliased() throws Throwable {
    doTest("A.groovy");
  }

  public void testSimpleOptimize() throws Throwable {
    doTest("A.groovy");
  }

  public void testCommented() throws Throwable {
    doTest("A.groovy");
  }

  public void testOptimizeExists() throws Throwable {
    doTest("A.groovy");
  }

  public void testOptimizeAlias() throws Throwable {
    doTest("A.groovy");
  }

  public void testFoldImports() throws Throwable {
    doTest("A.groovy");
  }

  public void testFoldImports2() throws Throwable {
    doTest("A.groovy");
  }

  public void testUntypedCall() throws Throwable {
    doTest("A.groovy");
  }

  public void testFoldImports3() throws Throwable {
    doTest("A.groovy");
  }

  public void testFoldImports4() throws Throwable {
    doTest("A.groovy");
  }

  public void testFoldImports5() throws Throwable {
    doTest("A.groovy");
  }

  public void testFixPoint() throws Throwable {
    doTest("A.groovy");
  }

  public void testUtilListMasked() throws Throwable {
    myFixture.addClass("package java.awt; public class List {}");
    doTest(getTestName(false) + ".groovy");
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
    doTest("A.groovy");
  }

  public void testSameFile() throws Throwable {
    doTest("A.groovy");
  }

  public void testSamePackage() throws Throwable {
    myFixture.addClass("package foo; public class Bar {}");
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".groovy", "foo/Foo.groovy"));
    doOptimizeImports();
    myFixture.checkResultByFile("result.test");
  }

  public void testQualifiedUsage() throws Throwable {
    myFixture.addClass("package foo; public class Bar {}");
    doTest(getTestName(false) + ".groovy");
  }

  public void testFileHeader() throws Throwable {
    doTest(getTestName(false) + ".groovy");
  }

  private void doTest(@NonNls String filePath) throws Throwable {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).clone();
    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(settings);
    settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 3;
    try {
      myFixture.configureByFile(filePath);

      doOptimizeImports();

      myFixture.checkResultByFile("result.test");

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
