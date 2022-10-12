// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.refactoring.optimizeImports

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors

class GroovyAddImportActionTest extends LightJavaCodeInsightFixtureTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST_REAL_JDK

  void testUseContext() {
    myFixture.addClass 'package foo; public class Log {}'
    myFixture.addClass 'package bar; public class Log {}'
    myFixture.addClass 'package bar; public class LogFactory { public static Log log(){} }'
    doTest('''\
public class Foo {
    Lo<caret>g l = bar.LogFactory.log();
}
''', '''\
import bar.Log

public class Foo {
    Lo<caret>g l = bar.LogFactory.log();
}
''')
  }

  void _testReferenceWithErrors() {
    myFixture.addClass 'package foo; public class Abc<X, Y> {}'
    doTest('''\
A<caret>bc<String, > foo = null
''', '''\
import foo.Abc

A<caret>bc<String, > foo = null
''')
  }

  void testNewifySupport() {
    myFixture.addClass 'package hello; public class Abc {}'
    doTest('''
import groovy.transform.CompileStatic

@Newify(pattern = /[A-Z][A-Za-z0-9_]+/)
@CompileStatic
void newifyImportsIncorrectlyMarkedAsUnused() {
    def e = Ab<caret>c()
}''', '''
import groovy.transform.CompileStatic
import hello.Abc

@Newify(pattern = /[A-Z][A-Za-z0-9_]+/)
@CompileStatic
void newifyImportsIncorrectlyMarkedAsUnused() {
    def e = Ab<caret>c()
}''')
  }

  void testNewifySupportForNestedClass() {
    myFixture.addClass 'package hello; public class Abc { public static class Cde {} }'
    doTest('''
import groovy.transform.CompileStatic

@Newify(pattern = /[A-Z][A-Za-z0-9_]+/)
@CompileStatic
void newifyImportsIncorrectlyMarkedAsUnused() {
    def e = Cd<caret>e()
}''', '''
import groovy.transform.CompileStatic
import hello.Abc.Cde

@Newify(pattern = /[A-Z][A-Za-z0-9_]+/)
@CompileStatic
void newifyImportsIncorrectlyMarkedAsUnused() {
    def e = Cd<caret>e()
}''')
  }

  private void doTest(String before, String after) {
    myFixture.configureByText 'a.groovy', before
    importClass()
    myFixture.checkResult after
  }

  private def importClass() {
    myFixture.launchAction(myFixture.findSingleIntention("Import class"))
  }


}
