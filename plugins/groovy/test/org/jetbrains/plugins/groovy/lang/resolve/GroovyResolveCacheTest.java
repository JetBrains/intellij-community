// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFileFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class GroovyResolveCacheTest extends GroovyLatestTest {
  @Test
  public void testDropCachesOnOutOfCodeBlockChange() {
    final Ref<Integer> counter = new Ref<>(0);

    AstTransformationSupport transformationSupport = new AstTransformationSupport() {

      @Override
      public void applyTransformation(@NotNull TransformationContext context) {
        counter.set(counter.get() + 1);
      }
    };
    AstTransformationSupport.EP_NAME.getPoint().registerExtension(transformationSupport, getFixture().getTestRootDisposable());
    GroovyFile file =
      (GroovyFile)getFixture().addFileToProject("_.groovy", "class Super { def foo() {} }");
    final GrTypeDefinition clazz = file.getTypeDefinitions()[0];

    Assert.assertEquals(0, (int)counter.get());
    Assert.assertEquals(6, clazz.getMethods().length);
    Assert.assertEquals(1, (int)counter.get());

    WriteCommandAction.runWriteCommandAction(getFixture().getProject(), () -> clazz.getMethods()[0].delete());

    Assert.assertEquals(1, (int)counter.get());
    Assert.assertEquals(5, clazz.getMethods().length);
    Assert.assertEquals(2, (int)counter.get());
  }

  @Ignore
  @Test
  public void testDoNotDropCachesOnCodeBlockChange() {
    final Ref<Integer> counter = new Ref<>(0);

    AstTransformationSupport transformationSupport = new AstTransformationSupport(){

      @Override
      public void applyTransformation(@NotNull TransformationContext context) {
        counter.set(counter.get() + 1);
      }
    };
    AstTransformationSupport.EP_NAME.getPoint().registerExtension(transformationSupport, getFixture().getTestRootDisposable());
    GroovyFile file =
      (GroovyFile)getFixture().addFileToProject("_.groovy", "class Super { def foo() { 1 } }");
    final GrTypeDefinition clazz = file.getTypeDefinitions()[0];

    Assert.assertEquals(0, (int)counter.get());
    Assert.assertEquals(6, clazz.getMethods().length);
    Assert.assertEquals(1, ((GrMethod)clazz.getMethods()[0]).getBlock().getStatements().length);
    Assert.assertEquals(1, (int)counter.get());

    WriteCommandAction.runWriteCommandAction(getFixture().getProject(),
         () -> ((GrMethod)clazz.getMethods()[0]).getBlock().getStatements()[0].delete());

    Assert.assertEquals(1, (int)counter.get());
    Assert.assertEquals(6, clazz.getMethods().length);
    Assert.assertEquals(0, ((GrMethod)clazz.getMethods()[0]).getBlock().getStatements().length);
    Assert.assertEquals(1, (int)counter.get());
  }

  @Test
  public void testDropCachesOnNonPhysicalContextChange() {
    PsiFileFactory fileFactory = PsiFileFactory.getInstance(getFixture().getProject());
    GroovyFile file =
      (GroovyFile)fileFactory.createFileFromText("a.groovy", GroovyFileType.GROOVY_FILE_TYPE, "class Super { @Deprecated void foo(){} }");
    GrTypeDefinition superClass = file.getTypeDefinitions()[0];

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getFixture().getProject());
    GrNewExpression expression =
      (GrNewExpression)factory.createExpressionFromText("new Super() { void foo(){} }", superClass);
    GrAnonymousClassDefinition subClass = expression.getAnonymousClassDefinition();

    Assert.assertNotNull(AnnotationUtil.findAnnotationInHierarchy(subClass.getMethods()[0], Deprecated.class));
    superClass.getMethods()[0].getModifierList().getAnnotations()[0].delete();
    Assert.assertNull(AnnotationUtil.findAnnotationInHierarchy(subClass.getMethods()[0], Deprecated.class));
  }
}
