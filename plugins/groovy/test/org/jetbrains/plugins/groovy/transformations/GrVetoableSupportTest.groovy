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
package org.jetbrains.plugins.groovy.transformations

import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiCall
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.SetupRule
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.transformations.impl.VetoableTransformationSupportKt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@CompileStatic
@RunWith(Parameterized)
class GrVetoableSupportTest {

  public @Rule SetupRule setupRule = new SetupRule()

  JavaCodeInsightTestFixture getFixture() {
    setupRule.testCase.fixture
  }

  @Parameterized.Parameters(name = "{0}")
  static Collection<Object[]> data() {
    [
      '@Vetoable on class'               : '@groovy.beans.Vetoable class Person {}',
      '@Vetoable on class @CompileStatic': '@groovy.transform.CompileStatic @groovy.beans.Vetoable class Person {}',
      '@Vetoable on field'               : 'class Person { @groovy.beans.Vetoable foo }',
      '@Vetoable on field @CompileStatic': '@groovy.transform.CompileStatic class Person { @groovy.beans.Vetoable foo }'
    ].collect { k, v ->
      [k, v] as Object[]
    }
  }

  @Parameterized.Parameter
  public String name

  @Parameterized.Parameter(1)
  public String clazzText

  private PsiClass clazz

  @Before
  void addPerson() {
    fixture.addFileToProject('Person.groovy', clazzText)
    clazz = fixture.findClass('Person')
    assert clazz
  }

  @Test
  void 'test groovy'() {
    fixture.enableInspections(GrUnresolvedAccessInspection, GroovyAssignabilityCheckInspection)
    [
      "new Person().addVetoableChangeListener {}",
      "new Person().addVetoableChangeListener('') {}",
      "new Person().removeVetoableChangeListener {}",
      "new Person().removeVetoableChangeListener('') {}",
      "new Person().fireVetoableChange('', null, null)",
      "new Person().getVetoableChangeListeners()",
      "new Person().getVetoableChangeListeners('')"
    ].each {
      def file = fixture.configureByText('test.groovy', it) as GroovyFile
      def call = file.statements.last() as GrCall
      def method = call.resolveMethod() as GrLightMethodBuilder
      assert method
      assert method.containingClass == clazz
      assert method.originInfo == VetoableTransformationSupportKt.ORIGIN_INFO
      assert method.getUserData(ResolveUtil.DOCUMENTATION_DELEGATE_FQN)
      fixture.checkHighlighting()
    }
  }

  @Test
  void 'test java'() {
    LanguageLevelProjectExtension.getInstance(fixture.project).languageLevel = LanguageLevel.HIGHEST
    [
      'new Person().addVetoableChangeListener(e -> {});',
      'new Person().addVetoableChangeListener("", e -> {});',
      'new Person().removeVetoableChangeListener(e -> {});',
      'new Person().removeVetoableChangeListener("", e -> {});',
      'new Person().fireVetoableChange("", null, null);',
      'new Person().getVetoableChangeListeners();',
      'new Person().getVetoableChangeListeners("");'
    ].each {
      def file = fixture.configureByText('Main.java', "class Main { void bar() {$it} }") as PsiJavaFile
      def call = (file.classes[0].methods[0].body.statements[0] as PsiExpressionStatement).expression as PsiCall
      def method = call.resolveMethod() as GrLightMethodBuilder
      assert method
      assert method.containingClass == clazz
      assert method.originInfo == VetoableTransformationSupportKt.ORIGIN_INFO
      fixture.checkHighlighting()
    }
  }
}
