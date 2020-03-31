// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.refactoring.optimizeImports
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
/**
 * @author peter
 */
class GroovyAddImportActionTest extends LightJavaCodeInsightFixtureTestCase {

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

  private void doTest(String before, String after) {
    myFixture.configureByText 'a.groovy', before
    importClass()
    myFixture.checkResult after
  }

  private def importClass() {
    myFixture.launchAction(myFixture.findSingleIntention("Import class"))
  }


}
