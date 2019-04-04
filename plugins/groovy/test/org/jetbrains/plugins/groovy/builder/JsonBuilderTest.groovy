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
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.junit.Test

import static org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor.GROOVY_LATEST

// TODO rewrite into Spock test
@CompileStatic
class JsonBuilderTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GROOVY_LATEST

  void 'test highlighting'() {
    fixture.configureByText 'a.groovy', '''\
def builder = new groovy.json.JsonBuilder()
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
      'builder.root()'                                   : 'java.util.Map',
      'builder.root {}'                                  : 'java.util.Map',
      'builder.root(a:1)'                                : 'java.util.Map',
      'builder.root([a:1])'                              : 'java.util.Map',
      'builder.root([a:1]) {}'                           : 'java.util.Map',
      'builder.root(a:1) {}'                             : 'java.util.Map',
      'builder.root(a:1, {})'                            : 'java.util.Map',
      'builder.root({}, a:1)'                            : 'java.util.Map',
      'builder.root([1,"",new Object()], {})'            : 'java.util.Map',
      'builder.root([1,"",new Object()] as Object[], {})': 'java.util.Map',
    ].each { text, returnType ->
      def file = fixture.configureByText('a.groovy', "def builder = new groovy.json.JsonBuilder(); $text") as GroovyFile
      def call = file.topStatements.last() as GrCallExpression
      assert call.resolveMethod()
      assert call.type.canonicalText == returnType
    }
  }

  void 'test builder inner calls resolve & return type'() {
    [
      'noArg()'                       : 'java.util.List',
      'singleArg(1)'                  : 'java.lang.Integer',
      'singleArg("")'                 : 'java.lang.String',
      'singleArg(new Object())'       : 'java.lang.Object',
      'doubleArgs([1,2,3], {})'       : 'java.util.List<java.lang.Integer>',
      'doubleArgs([] as Number[], {})': 'java.util.List<java.lang.Number>',
      'varArgs(1,2,3)'                : 'java.util.List<java.lang.Integer>',
      'varArgs(1,2l,3d)'              : 'java.util.List<java.lang.Number>',
      'varArgs(1,2l,3d, new Object())': 'java.util.List<java.lang.Object>',
    ].each { callText, returnType ->
      [
        "builder.root {<caret>$callText}",
        "builder.root([a:1]) {<caret>$callText}",
        "builder.root({<caret>$callText}, a:1)",
      ].each { text ->
        fixture.configureByText 'a.groovy', "def builder = new groovy.json.JsonBuilder(); $text"
        def reference = fixture.getReferenceAtCaretPosition() as GrReferenceExpression
        assert reference.resolve() instanceof PsiMethod
        assert reference.type.canonicalText == returnType
      }
    }
  }

  @Test
  void 'test builder delegate inner calls resolve & return type'() {
    [
      'noArg()'                       : 'java.util.List',
      'singleArg(1)'                  : 'java.lang.Integer',
      'singleArg("")'                 : 'java.lang.String',
      'singleArg(new Object())'       : 'java.lang.Object',
      'doubleArgs([1,2,3], {})'       : 'java.util.List<java.lang.Integer>',
      'doubleArgs([] as Number[], {})': 'java.util.List<java.lang.Number>',
      'varArgs(1,2,3)'                : 'java.util.List<java.lang.Integer>',
      'varArgs(1,2l,3d)'              : 'java.util.List<java.lang.Number>',
      'varArgs(1,2l,3d, new Object())': 'java.util.List<java.lang.Object>',
    ].each { innerCallText, returnType ->
      [
        "doubleArgs([1,2,3], {<caret>$innerCallText})",
        "doubleArgs([] as Number[], {<caret>$innerCallText})"
      ].each { callText ->
        [
          "builder.root {$callText}",
          "builder.root([a:1]) {$callText}",
          "builder.root({$callText}, a:1)",
        ].each { text ->
          fixture.configureByText 'a.groovy', "def builder = new groovy.json.JsonBuilder(); $text"
          def reference = fixture.getReferenceAtCaretPosition() as GrReferenceExpression
          assert reference.resolve() instanceof PsiMethod
          assert reference.type.canonicalText == returnType
        }
      }
    }
  }
}