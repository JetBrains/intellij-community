/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.builder

import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.compiled.ClsMethodImpl
import com.intellij.psi.impl.light.LightElement
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

@CompileStatic
class StreamingJsonBuilderTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  void 'test highlighting'() {
    myFixture.configureByText 'a.groovy', '''
def builder = new groovy.json.StreamingJsonBuilder(null)
builder.people {
    person {
        string "fdsasdf"
        mapCall(
                city: 'A',
                country: 'B',
                zip: 12345,
        )
        boolCall true
        varArgs '1111', 22222
        empty()
        cc {
          foobar()
          hellYeah(1,2,3)
        }
        <warning>someProperty</warning>
    }
    <warning>someProperty</warning>
}

builder.<warning>root</warning>
builder.root<warning>(new Object())</warning>
builder.root<warning>(new Object[0])</warning>
builder.root<warning>([new Object(), new Object()])</warning>
builder.root<warning>([], new Object(), {})</warning>
'''
    fixture.enableInspections GroovyAssignabilityCheckInspection, GrUnresolvedAccessInspection
    fixture.checkHighlighting true, false, true
  }

  void 'test builder calls resolve & return type'() {
    [
      "builder.root()",
      "builder.root {}",
      "builder.root(a: 1, b: 2)",
      "builder.root(a: 1, b: 2) {}",
      "builder.root([1, 2, 3, 4]) {}",
      "builder.root([] as Integer[], {})",
    ].each { text ->
      def file = fixture.configureByText('a.groovy', "def builder = new groovy.json.StreamingJsonBuilder(null);$text") as GroovyFile
      def call = file.topStatements.last() as GrCallExpression
      def method = call.resolveMethod()
      assert method
      assert call.type.canonicalText == 'groovy.json.StreamingJsonBuilder'
    }
  }

  void 'test builder inner calls resolve & return type'() {
    [
      "noArg()",
      "singleArg(1)",
      "singleArg(new Object())",
      "singleArg {}",
      "singleArg([:])",
      "doubleArg([:]) {}",
      "doubleArg(1, 2)",
      "doubleArg([], {})",
      "doubleArg(new Object[0]) {}",
      "doubleArg(new Object[0], {})",
      "varArg(1, 2, 3)",
      "varArg(1, 2d, '')",
      "varArg(new Object(), [], {}, a: 1, 2d, [:], '')",
    ].each { callText ->
      [
        "builder.root {<caret>$callText}",
        "builder.root(a: 1, b: 2) {<caret>$callText}",
        "builder.root([1, 2, 3, 4]) {<caret>$callText}",
        "builder.root([] as Integer[], {<caret>$callText})",
      ].each { text ->
        doTest(text)
      }
    }
  }

  void 'test builder delegate inner calls resolve & return type'() {
    [
      "noArg()",
      "singleArg(1)",
      "singleArg(new Object())",
      "singleArg {}",
      "singleArg([:])",
      "doubleArg([:]) {}",
      "doubleArg(1, 2)",
      "doubleArg([], {})",
      "doubleArg(new Object[0]) {}",
      "doubleArg(new Object[0], {})",
      "varArg(1, 2, 3)",
      "varArg(1, 2d, '')",
      "varArg(new Object(), [], {}, a: 1, 2d, [:], '')",
    ].each { innerCallText ->
      [
        "singleArg {<caret>$innerCallText}",
        "doubleArg([:]) {<caret>$innerCallText}",
        "doubleArg([], {<caret>$innerCallText})",
        "doubleArg(new Object[0], {<caret>$innerCallText})",
        "varArg(new Object(), [], {<caret>$innerCallText}, a: 1, 2d, [:], '')",
      ].each { callText ->
        [
          "builder.root {$callText}",
          "builder.root(a: 1, b: 2) {$callText}",
          "builder.root([1, 2, 3, 4]) {$callText}",
          "builder.root([] as Integer[], {$callText})",
        ].each { text ->
          doTest(text)
        }
      }
    }
  }

  void 'test owner first'() {
    myFixture.configureByText 'a.groovy', '''\
def foo(String s) {}
new groovy.json.StreamingJsonBuilder().root {
  fo<caret>o ""
}
'''
    def resolved = myFixture.file.findReferenceAt(myFixture.caretOffset)?.resolve()
    assert resolved instanceof GrMethod
    assert !(resolved instanceof LightElement)
    assert resolved.physical
  }

  private void doTest(text) {
    fixture.configureByText 'a.groovy', "def builder = new groovy.json.StreamingJsonBuilder(); $text"
    def reference = fixture.getReferenceAtCaretPosition() as GrReferenceExpression
    assert reference.resolve() instanceof PsiMethod && reference.type.canonicalText == 'java.lang.Object' : text
  }

  void 'test do not override existing methods'() {
    def file = myFixture.configureByText('a.groovy', '''
new groovy.json.StreamingJsonBuilder().cal<caret>l {}
''') as GroovyFile
    def call = file.topStatements.last() as GrCallExpression
    def method = call.resolveMethod()
    assert method
    assert method instanceof ClsMethodImpl
  }
}
