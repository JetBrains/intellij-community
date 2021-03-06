// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiFileFactory
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.junit.Ignore
import org.junit.Test

import static com.intellij.codeInsight.AnnotationUtil.findAnnotationInHierarchy

@CompileStatic
class GroovyResolveCacheTest extends GroovyLatestTest {

  @Test
  void 'test drop caches on out of code block change'() {
    int counter = 0
    AstTransformationSupport.EP_NAME.getPoint().registerExtension({
      counter++
    } as AstTransformationSupport, fixture.testRootDisposable)
    def file = fixture.addFileToProject(
      '_.groovy', 'class Super { def foo() {} }'
    ) as GroovyFile
    def clazz = file.typeDefinitions.first()

    assert counter == 0
    assert clazz.methods.size() == 6
    assert counter == 1

    WriteCommandAction.runWriteCommandAction(fixture.project) {
      clazz.methods.first().delete()
    }

    assert counter == 1
    assert clazz.methods.size() == 5
    assert counter == 2
  }

  @Ignore
  @Test
  void 'test do not drop caches on code block change'() {
    int counter = 0
    AstTransformationSupport.EP_NAME.getPoint().registerExtension({
      counter++
    } as AstTransformationSupport, fixture.testRootDisposable)
    def file = fixture.addFileToProject(
      '_.groovy', 'class Super { def foo() { 1 } }'
    ) as GroovyFile
    def clazz = file.typeDefinitions.first()

    assert counter == 0
    assert clazz.methods.size() == 6
    assert (clazz.methods.first() as GrMethod).block.statements.size() == 1
    assert counter == 1

    WriteCommandAction.runWriteCommandAction(fixture.project) {
      (clazz.methods.first() as GrMethod).block.statements.first().delete()
    }

    assert counter == 1
    assert clazz.methods.size() == 6
    assert (clazz.methods.first() as GrMethod).block.statements.size() == 0
    assert counter == 1
  }

  @Test
  void 'test drop caches on non physical context change'() {
    def fileFactory = PsiFileFactory.getInstance(fixture.project)
    def file = fileFactory.createFileFromText(
      "a.groovy", GroovyFileType.GROOVY_FILE_TYPE, "class Super { @Deprecated void foo(){} }"
    ) as GroovyFile
    def superClass = file.typeDefinitions.first()

    def factory = GroovyPsiElementFactory.getInstance(fixture.project)
    def expression = factory.createExpressionFromText("new Super() { void foo(){} }", superClass) as GrNewExpression
    def subClass = expression.anonymousClassDefinition

    assert findAnnotationInHierarchy(subClass.methods.first(), Deprecated.class) != null
    superClass.methods.first().modifierList.annotations.first().delete()
    assert findAnnotationInHierarchy(subClass.methods.first(), Deprecated.class) == null
  }
}
