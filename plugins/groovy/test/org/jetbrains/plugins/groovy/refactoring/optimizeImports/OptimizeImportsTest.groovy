/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.optimizeImports
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyAccessibilityInspection
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings
import org.jetbrains.plugins.groovy.editor.GroovyImportOptimizer
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author ilyas
 */
public class OptimizeImportsTest extends LightGroovyTestCase {

  final String basePath = "${TestUtils.testDataPath}optimizeImports/"

  @Override
  protected void setUp() {
    super.setUp()
    CodeInsightSettings.instance.OPTIMIZE_IMPORTS_ON_THE_FLY = true
    ((CodeInsightTestFixtureImpl)myFixture).canChangeDocumentDuringHighlighting(true)
  }

  @Override
  protected void tearDown() {
    CodeInsightSettings.instance.OPTIMIZE_IMPORTS_ON_THE_FLY = false
    super.tearDown()
  }

  public void testNewline() {
    doTest();
  }

  public void testAliased() {
    doTest();
  }

  public void testSimpleOptimize() {
    doTest();
  }

  public void testCommented() {
    doTest();
  }

  public void testOptimizeExists() {
    doTest();
  }

  public void testOptimizeAlias() {
    doTest();
  }

  public void testFoldImports() {
    doTest();
  }

  public void testFoldImports2() {
    doTest();
  }

  public void testUntypedCall() {
    doTest();
  }

  public void testFoldImports3() {
    doTest();
  }

  public void testFoldImports4() {
    doTest();
  }

  public void testFoldImports5() {
    doTest();
  }

  public void testFixPoint() {
    doTest();
  }

  public void testPreserveImportAnnotations() { doTest(); }

  public void testUtilListMasked() {
    myFixture.addClass("package java.awt; public class List {}");
    doTest();
  }

  public void testExtraLineFeed() {
    myFixture.addClass("package groovy.io; public class EncodingAwareBufferedWriter {}");
    myFixture.addClass("package groovy.io; public class PlatformLineWriter {}");
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".groovy", "foo/bar.groovy"));
    doOptimizeImports();
    doOptimizeImports();
    myFixture.checkResultByFile(getTestName(false) + ".groovy");
  }

  public void testSemicolons() {
    doTest();
  }

  public void testSameFile() {
    doTest();
  }

  public void testSamePackage() {
    myFixture.addClass("package foo; public class Bar {}");
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".groovy", "foo/Foo.groovy"));
    doOptimizeImports();
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testQualifiedUsage() {
    myFixture.addClass("package foo; public class Bar {}");
    doTest();
  }

  public void testFileHeader() {
    doTest();
  }

  public void testRemoveImplicitlyImported() { doTest(); }
  public void testRemoveImplicitlyDemandImported() { doTest(); }
  public void testDontRemoveRedImports() { doTest(); }
  public void testDontRemoveRedImports2() { doTest(); }
  public void testDontRemoveRedImports3() { doTest(); }
  public void testDontRemoveRedImports4() { doTest(); }
  public void testDontRemoveRedImports5() { doTest(); }
  public void testDontRemoveRedImports6() { doTest(); }
  public void testDontRemoveRedImports7() { doTest(); }
  public void testDontRemoveRedImports8() { doTest(); }
  public void testDontRemoveRedImports9() { doTest(); }

  public void testRemoveSamePackaged() {
    myFixture.addClass("package foo.bar; public class Aaaa {}");
    myFixture.addClass("package foo.bar; public class Bbbb {}");
    myFixture.addClass("package foo.bar; public class Zzzz {}");
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".groovy", "foo/Foo.groovy"));
    doOptimizeImports();
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testJustWrongImport() throws Exception {
    myFixture.configureByText("a.groovy", "import a.b.c.d; final d c;");
    doOptimizeImports();
    myFixture.checkResult("import a.b.c.d; final d c;");
  }

  public void testCleanBeforeJavadoc() throws Exception {
    myFixture.configureByText("a.groovy", """import javax.swing.JFrame

/**
 * some javadoc
 */
class Fooxx<caret>{
}""");
    myFixture.type ' '
    myFixture.doHighlighting()
//    doOptimizeImports();
    myFixture.checkResult("""/**
 * some javadoc
 */
class Fooxx <caret>{
}""");
  }

  private void doTest() {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).clone();
    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(settings)
    settings.getCustomSettings(GroovyCodeStyleSettings.class).CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 3;
    try {
      myFixture.configureByFile(getTestName(false) + ".groovy");

      doOptimizeImports();
      PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
      myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
    }
    finally {
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    }
  }

  private void doOptimizeImports() {
    GroovyImportOptimizer optimizer = new GroovyImportOptimizer();
    final Runnable runnable = optimizer.processFile(myFixture.file);

    CommandProcessor.instance.executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.application.runWriteAction(runnable);
      }
    }, "Optimize imports", null);
  }

  public void testOptimizeOnTheFly() {
    myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("a.groovy", "import java.lang.String<caret>").virtualFile)
    //myFixture.configureByText "a.groovy", "import java.lang.String<caret>"
    myFixture.type '\n\nfoo'
    myFixture.doHighlighting()
    myFixture.checkResult "foo<caret>"
  }

  public void testNoOptimizeOnTheFlyDuringEditing() {
    myFixture.configureByText "a.groovy", "import java.util.String<caret>"
    myFixture.type '\nimport java.lang.String'
    myFixture.doHighlighting()
    myFixture.checkResult "import java.util.String\nimport java.lang.String<caret>"
  }

  public void testNoOptimizeOnDummyChange() {
    myFixture.addClass "package foo; public class Foo {}"
    myFixture.configureByText "a.groovy", "import foo.Foo\n\ndef foo() { <caret> Foo f}"
    myFixture.doHighlighting()
    myFixture.type '\n'
    myFixture.doHighlighting()
    myFixture.checkResult "import foo.Foo\n\ndef foo() { \n    <caret>Foo f}"
  }

  public void testUnusedImportsForImportsOnDemand() {
    myFixture.enableInspections(new GroovyAccessibilityInspection())
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".groovy")
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testSorting() {
    myFixture.addClass("package foo; public class Foo{}");
    myFixture.addClass("package foo; public class Bar{public static void foo0(){}}");
    myFixture.addClass("package java.test; public class Test{public static void foo(){}}");
    myFixture.addClass("package java.test2; public class Test2{public static void foo2(){}}");
    myFixture.addClass("package test; public class Alias{public static void test(){}}")
    myFixture.addClass("package test; public class Alias2{public static void test2(){}}")

    myFixture.configureByText('__a.groovy', '''\
package pack


import foo.Foo
import foo.Bar
import java.test.Test
import java.test2.Test2
import static foo.Bar.foo0
import static java.test.Test.foo
import static java.test2.Test2.*
import static test.Alias.test as aliased
import static test.Alias2.test2 as aliased2

new Foo()
new Bar()
new Test()
new Test2()
foo()
foo0()
foo1()
aliased()
aliased2()
''')

    doOptimizeImports()

    myFixture.checkResult('''\
package pack

import foo.Bar
import foo.Foo

import java.test.Test
import java.test2.Test2

import static foo.Bar.foo0
import static java.test.Test.foo
import static test.Alias.test as aliased
import static test.Alias2.test2 as aliased2

new Foo()
new Bar()
new Test()
new Test2()
foo()
foo0()
foo1()
aliased()
aliased2()
''')

  }

  void testAnnotationOnUnusedImport1() {
    myFixture.addClass('package groovyx.gpars; public class GParsPool{}')
    myFixture.addClass('package groovyx.gpars; public class GParsExecutorsPool{}')

    myFixture.configureByText('_.groovy', '''\
@Grab(group='org.codehaus.gpars', module='gpars', version='0.12')
import groovyx.gpars.GParsPool
import groovyx.gpars.GParsExecutorsPool

GParsExecutorsPool oi
''')
    doOptimizeImports()

    myFixture.checkResult('''\
@Grab(group = 'org.codehaus.gpars', module = 'gpars', version = '0.12')
import groovyx.gpars.GParsExecutorsPool

GParsExecutorsPool oi
''')
  }

  void testAnnotationOnUnusedImport2() {
    myFixture.addClass('package groovyx.gpars; public class GParsPool{}')

    myFixture.configureByText('_.groovy', '''\
@Grab(group='org.codehaus.gpars', module='gpars', version='0.12')
import groovyx.gpars.GParsPool
''')
    doOptimizeImports()

    myFixture.checkResult('''\
@Grab(group = 'org.codehaus.gpars', module = 'gpars', version = '0.12')
import java.lang.Object
''')
  }

  void testAnnotationOnUnusedImport3() {
    myFixture.addClass('package groovyx.gpars; public class GParsPool{}')
    myFixture.addClass('package groovyx.gpars; public class GParsExecutorsPool{}')

    myFixture.configureByText('_.groovy', '''\
@Grab(group='org.codehaus.gpars', module='gpars', version='0.12')
@Grab(group='org.codehaus.gpars', module='gpars', version='0.12')
import groovyx.gpars.GParsPool
import groovyx.gpars.GParsExecutorsPool

GParsExecutorsPool oi
''')
    doOptimizeImports()

    myFixture.checkResult('''\
@Grab(group = 'org.codehaus.gpars', module = 'gpars', version = '0.12')
@Grab(group = 'org.codehaus.gpars', module = 'gpars', version = '0.12')
import groovyx.gpars.GParsExecutorsPool

GParsExecutorsPool oi
''')
  }

  void 'test do not remove space between package and imports'() {
    myFixture.addClass('package some; class Import {}')
    myFixture.addClass('package some; class ImportToBeDeleted {}')
    myFixture.addClass('package some; class OtherImport {}')
    myFixture.configureByText('_.groovy', '''\
package pkg

import some.OtherImport
import some.Import
import some.ImportToBeDeleted

class MyClass {
  def a = new Import()
  def b = new OtherImport()
}
''')
    doOptimizeImports()
    myFixture.checkResult '''\
package pkg

import some.Import
import some.OtherImport

class MyClass {
  def a = new Import()
  def b = new OtherImport()
}
'''
  }
}
