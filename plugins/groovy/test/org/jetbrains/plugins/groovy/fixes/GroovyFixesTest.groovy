/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.fixes

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyConstantIfStatementInspection
import org.jetbrains.plugins.groovy.codeInspection.style.JavaStylePropertiesInvocationInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
/**
 * @author peter
 */
class GroovyFixesTest extends LightCodeInsightFixtureTestCase {

  void testSuppressForIfStatement() throws Throwable {
    myFixture.enableInspections new GroovyConstantIfStatementInspection()
    myFixture.configureByText("a.groovy", """
<caret>if (true) {
  aaa
}""")
    myFixture.launchAction(myFixture.findSingleIntention("Suppress for statement"))
    myFixture.checkResult """//noinspection GroovyConstantIfStatement
if (true) {
  aaa
}"""
  }

  void testShallowChangeToGroovyStylePropertyAccess() throws Throwable {
    myFixture.enableInspections new JavaStylePropertiesInvocationInspection()
    myFixture.configureByText "a.groovy", """class GroovyClasss {
  def initializer
  def foo() {
    setInitializer({
      <caret>println "hello"
    })
  }
}

"""
    assertEmpty myFixture.filterAvailableIntentions("Change to Groovy-style property reference")
  }

  void testSecondAnnotationSuppression() {
    myFixture.enableInspections(new GrUnresolvedAccessInspection())
    myFixture.configureByText "a.groovy", """\
class FooBarGoo {
  @SuppressWarnings(["GroovyParameterNamingConvention"])
  def test(Object abc) {
    abc.d<caret>ef()
  }
}
"""
    myFixture.launchAction(myFixture.findSingleIntention("Suppress for method"))
    myFixture.checkResult """\
class FooBarGoo {
  @SuppressWarnings(["GroovyParameterNamingConvention", "GrUnresolvedAccess"])
  def test(Object abc) {
    abc.def()
  }
}
"""
  }

  void testSecondAnnotationSuppression2() {
    myFixture.enableInspections new GrUnresolvedAccessInspection()
    myFixture.configureByText "a.groovy", """class FooBarGoo {
  @SuppressWarnings("GroovyParameterNamingConvention")
  def test(Object abc) {
    abc.d<caret>ef()
  }
}
"""
    myFixture.launchAction(myFixture.findSingleIntention("Suppress for method"))
    myFixture.checkResult """class FooBarGoo {
  @SuppressWarnings(["GroovyParameterNamingConvention", "GrUnresolvedAccess"])
  def test(Object abc) {
    abc.def()
  }
}
"""
  }

  void testFixPackageName() {
    myFixture.configureByText('Foo.groovy', '''\
#!/usr/bin/groovy

class Foo {}
''')
    WriteCommandAction.runWriteCommandAction project, { ((GroovyFile)myFixture.file).packageName = 'foo' }
    myFixture.checkResult '''\
#!/usr/bin/groovy
package foo

class Foo {}
'''

  }

}
