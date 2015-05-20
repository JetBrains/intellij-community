/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
/**
 * @author peter
 */
public class GroovyAddImportActionTest extends LightCodeInsightFixtureTestCase {

  public void testUseContext() {
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

  void testReferenceWithErrors() {
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
