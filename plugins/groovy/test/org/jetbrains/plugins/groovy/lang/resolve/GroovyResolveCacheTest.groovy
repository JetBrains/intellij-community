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
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport

import static com.intellij.codeInsight.AnnotationUtil.findAnnotationInHierarchy

@CompileStatic
class GroovyResolveCacheTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  void 'test drop caches on out of code block change'() {
    int counter = 0
    PlatformTestUtil.registerExtension(AstTransformationSupport.EP_NAME, {
      counter++
    } as AstTransformationSupport, myFixture.testRootDisposable)
    def file = fixture.addFileToProject(
      '_.groovy', 'class Super { def foo() {} }'
    ) as GroovyFile
    def clazz = file.typeDefinitions.first()

    assert counter == 0
    assert clazz.methods.size() == 6
    assert counter == 1

    WriteCommandAction.runWriteCommandAction(project) {
      clazz.methods.first().delete()
    }

    assert counter == 1
    assert clazz.methods.size() == 5
    assert counter == 2
  }

  void 'test do not drop caches on code block change'() {
    int counter = 0
    PlatformTestUtil.registerExtension(AstTransformationSupport.EP_NAME, {
      counter++
    } as AstTransformationSupport, myFixture.testRootDisposable)
    def file = fixture.addFileToProject(
      '_.groovy', 'class Super { def foo() { 1 } }'
    ) as GroovyFile
    def clazz = file.typeDefinitions.first()

    assert counter == 0
    assert clazz.methods.size() == 6
    assert (clazz.methods.first() as GrMethod).block.statements.size() == 1
    assert counter == 1

    WriteCommandAction.runWriteCommandAction(project) {
      (clazz.methods.first() as GrMethod).block.statements.first().delete()
    }

    assert counter == 1
    assert clazz.methods.size() == 6
    assert (clazz.methods.first() as GrMethod).block.statements.size() == 0
    assert counter == 1
  }

  void 'test drop caches on non physical context change'() {
    def fileFactory = PsiFileFactory.getInstance(project)
    def file = fileFactory.createFileFromText(
      "a.groovy", GroovyFileType.GROOVY_FILE_TYPE, "class Super { @Deprecated void foo(){} }"
    ) as GroovyFile
    def superClass = file.typeDefinitions.first()

    def factory = GroovyPsiElementFactory.getInstance(project)
    def expression = factory.createExpressionFromText("new Super() { void foo(){} }", superClass) as GrNewExpression
    def subClass = expression.anonymousClassDefinition

    assert findAnnotationInHierarchy(subClass.methods.first(), Deprecated.class) != null
    superClass.methods.first().modifierList.annotations.first().delete()
    assert findAnnotationInHierarchy(subClass.methods.first(), Deprecated.class) == null
  }
}
