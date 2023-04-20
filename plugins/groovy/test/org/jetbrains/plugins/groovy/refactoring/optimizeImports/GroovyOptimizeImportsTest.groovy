// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.refactoring.optimizeImports

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.CodeInsightWorkspaceSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.editor.GroovyImportOptimizer
import org.jetbrains.plugins.groovy.util.TestUtils

import java.util.function.Consumer

class GroovyOptimizeImportsTest extends LightGroovyTestCase {

  final String basePath = "${TestUtils.testDataPath}optimizeImports/"

  @Override
  void setUp() {
    super.setUp()
    CodeInsightWorkspaceSettings.getInstance(project).setOptimizeImportsOnTheFly(true, testRootDisposable)
    ((CodeInsightTestFixtureImpl)myFixture).canChangeDocumentDuringHighlighting(true)
  }

  void testNewline() {
    doTest()
  }

  void testAliased() {
    doTest()
  }

  void testSimpleOptimize() {
    doTest()
  }

  void testCommented() {
    doTest()
  }

  void testOptimizeExists() {
    doTest()
  }

  void testOptimizeAlias() {
    doTest()
  }

  void testFoldImports() {
    doTest()
  }

  void testFoldImports2() {
    doTest()
  }

  void testUntypedCall() {
    doTest()
  }

  void testFoldImports3() {
    doTest()
  }

  void testFoldImports4() {
    doTest()
  }

  void testFoldImports5() {
    doTest()
  }

  void testFixPoint() {
    doTest()
  }

  void testPreserveImportAnnotations() { doTest() }

  void testUtilListMasked() {
    myFixture.addClass("package java.awt; public class List {}")
    doTest()
  }

  void testExtraLineFeed() {
    myFixture.addClass("package groovy.io; public class EncodingAwareBufferedWriter {}")
    myFixture.addClass("package groovy.io; public class PlatformLineWriter {}")
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".groovy", "foo/bar.groovy"))
    doOptimizeImports()
    doOptimizeImports()
    myFixture.checkResultByFile(getTestName(false) + ".groovy")
  }

  void testSemicolons() {
    doTest()
  }

  void testSameFile() {
    doTest()
  }

  void testSamePackage() {
    myFixture.addClass("package foo; public class Bar {}")
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".groovy", "foo/Foo.groovy"))
    doOptimizeImports()
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy")
  }

  void testQualifiedUsage() {
    myFixture.addClass("package foo; public class Bar {}")
    doTest()
  }

  void testFileHeader() {
    doTest()
  }

  void testRemoveImplicitlyImported() { doTest() }

  void testRemoveImplicitlyDemandImported() { doTest() }

  void testDontRemoveRedImports() { doTest() }

  void testDontRemoveRedImports2() { doTest() }

  void testDontRemoveRedImports3() { doTest() }

  void testDontRemoveRedImports4() { doTest() }

  void testDontRemoveRedImports5() { doTest() }

  void testDontRemoveRedImports6() { doTest() }

  void testDontRemoveRedImports7() { doTest() }

  void testDontRemoveRedImports8() { doTest() }

  void testDontRemoveRedImports9() { doTest() }

  void testRemoveSamePackaged() {
    myFixture.addClass("package foo.bar; public class Aaaa {}")
    myFixture.addClass("package foo.bar; public class Bbbb {}")
    myFixture.addClass("package foo.bar; public class Zzzz {}")
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".groovy", "foo/Foo.groovy"))
    doOptimizeImports()
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy")
  }

  void testJustWrongImport() throws Exception {
    myFixture.configureByText("a.groovy", "import a.b.c.d; final d c;")
    doOptimizeImports()
    myFixture.checkResult("import a.b.c.d; final d c;")
  }

  void testCleanBeforeJavadoc() throws Exception {
    myFixture.configureByText("a.groovy", """import javax.swing.JFrame

/**
 * some javadoc
 */
class Fooxx<caret>{
}""")
    myFixture.type ' '
    myFixture.doHighlighting()
//    doOptimizeImports();
    myFixture.checkResult("""/**
 * some javadoc
 */
class Fooxx <caret>{
}""")
  }

  void testInnerInnerClass() { doTest() }

  private void doTest() {
    CodeStyle.doWithTemporarySettings(
      getProject(), CodeStyle.getSettings(getProject()),
      new Consumer<CodeStyleSettings>() {
        @Override
        void accept(CodeStyleSettings settings) {
          myFixture.configureByFile(getTestName(false) + ".groovy")

          doOptimizeImports()
          PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting()
          myFixture.checkResultByFile(getTestName(false) + "_after.groovy")
        }
      })
  }

  private void doOptimizeImports() {
    GroovyImportOptimizer optimizer = new GroovyImportOptimizer()
    final Runnable runnable = optimizer.processFile(myFixture.file)

    CommandProcessor.instance.executeCommand(project, new Runnable() {
      @Override
      void run() {
        ApplicationManager.application.runWriteAction(runnable)
      }
    }, "Optimize imports", null)
  }

  void testOptimizeOnTheFly() {
    myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("a.groovy", "import java.lang.String<caret>").virtualFile)
    //myFixture.configureByText "a.groovy", "import java.lang.String<caret>"
    myFixture.type '\n\nfoo'
    myFixture.doHighlighting()
    myFixture.checkResult "foo<caret>"
  }

  void testNoOptimizeOnTheFlyDuringEditing() {
    myFixture.configureByText "a.groovy", "import java.util.String<caret>"
    myFixture.type '\nimport java.lang.String'
    myFixture.doHighlighting()
    myFixture.checkResult "import java.util.String\nimport java.lang.String<caret>"
  }

  void testNoOptimizeOnDummyChange() {
    myFixture.addClass "package foo; public class Foo {}"
    myFixture.configureByText "a.groovy", "import foo.Foo\n\ndef foo() { <caret> Foo f}"
    myFixture.doHighlighting()
    myFixture.type '\n'
    myFixture.doHighlighting()
    myFixture.checkResult "import foo.Foo\n\ndef foo() { \n    <caret>Foo f}"
  }

  void testSorting() {
    myFixture.addClass("package foo; public class Foo{}")
    myFixture.addClass("package foo; public class Bar{public static void foo0(){}}")
    myFixture.addClass("package java.test; public class Test{public static void foo(){}}")
    myFixture.addClass("package java.test2; public class Test2{public static void foo2(){}}")
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

  void 'test @Newify with pattern'() {
    myFixture.addClass 'package hello; public class Abc {}'
    myFixture.configureByText('_.groovy', '''\
import groovy.transform.CompileStatic
import hello.Abc

import java.lang.Integer

@Newify(pattern = /[A-Z][A-Za-z0-9_]+/)
@CompileStatic
void newifyImportsIncorrectlyMarkedAsUnused() {
    final a = Integer(1)
    def b = Abc()
}
''')
    doOptimizeImports()
    myFixture.checkResult('''\
import groovy.transform.CompileStatic
import hello.Abc

@Newify(pattern = /[A-Z][A-Za-z0-9_]+/)
@CompileStatic
void newifyImportsIncorrectlyMarkedAsUnused() {
    final a = Integer(1)
    def b = Abc()
}
''')
  }

  void 'test annotated unresolved import'() {
    doTest '''\
import groovy.xml.XmlUtil
import javax.security.auth.AuthPermission
@Grab("coordinates")
import org.foo.Bar

XmlUtil xx
Bar bar
''', '''\
import groovy.xml.XmlUtil
@Grab("coordinates")
import org.foo.Bar

XmlUtil xx
Bar bar
'''
  }

  void 'test same package'() {
    myFixture.addClass("package foo; public class A {}")
    doTest("""package foo; import foo.A; class B { A a; }""",
           """\
package foo

class B { A a; }""")
  }

  private void doTest(String before, String after) {
    myFixture.configureByText '_.groovy', before
    doOptimizeImports()
    myFixture.checkResult after
  }
}
