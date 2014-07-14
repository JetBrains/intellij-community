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
package org.jetbrains.plugins.groovy.refactoring.convertJavaToGroovy

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue
import org.jetbrains.plugins.groovy.lang.psi.impl.AnnotationArgConverter

/**
 * Created by Max Medvedev on 8/19/13
 */
class ConvertAnnotationMemberValueTest extends LightCodeInsightFixtureTestCase {
  void testSimpleExpression1() {
    doTest('1 + 1', '1 + 1')
  }

  void testSimpleExpression2() {
    doTest('foo(bar)', 'foo(bar)')
  }

  void testSimpleAnnotation1() {
    doTest('@A', '@A')
  }

  void testSimpleAnnotation2() {
    doTest('@A()', '@A')
  }

  void testSimpleAnnotation3() {
    doTest('@A(1, value = 2)', '@A(1,value=2)')
  }

  void testAnnotationInitializer() {
    doTest('@A({1, 2, 3})', '@A([1,2,3])')
  }

  void testArrayInitializer() {
    doTest('new int[]{1, 2, 3}', '([1,2,3] as int[])')
  }

  void doTest(@NotNull String java, @NotNull String expectedGroovy) {
    myFixture.configureByText(JavaFileType.INSTANCE, '@A(' + java + ') class Clazz{}')

    PsiJavaFile file = myFixture.file as PsiJavaFile
    PsiAnnotationMemberValue value = file.classes[0].modifierList.annotations[0].parameterList.attributes[0].value
    GrAnnotationMemberValue result = new AnnotationArgConverter().convert(value)

    assertEquals(expectedGroovy, result.text)
  }
}
