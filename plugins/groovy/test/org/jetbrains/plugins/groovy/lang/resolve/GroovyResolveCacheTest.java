// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFileFactory;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class GroovyResolveCacheTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = lightJavaCodeInsightFixture(project);

  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Test
  public void testDropCachesOnOutOfCodeBlockChange() {
    final Ref<Integer> counter = new Ref<>(0);

    runInEdtAndWait(() -> {
      AstTransformationSupport transformationSupport = new AstTransformationSupport() {

        @Override
        public void applyTransformation(@NotNull TransformationContext context) {
          counter.set(counter.get() + 1);
        }
      };
      AstTransformationSupport.EP_NAME.getPoint().registerExtension(transformationSupport, getFixture().getTestRootDisposable());
    });
    GroovyFile file =
      (GroovyFile)getFixture().addFileToProject("_.groovy", "class Super { def foo() {} }");
    runInEdtAndWait(() -> {
      final GrTypeDefinition clazz = file.getTypeDefinitions()[0];

      Assertions.assertEquals(0, (int)counter.get());
      Assertions.assertEquals(6, clazz.getMethods().length);
      Assertions.assertEquals(1, (int)counter.get());

      WriteCommandAction.runWriteCommandAction(getFixture().getProject(), () -> clazz.getMethods()[0].delete());

      Assertions.assertEquals(1, (int)counter.get());
      Assertions.assertEquals(5, clazz.getMethods().length);
      Assertions.assertEquals(2, (int)counter.get());
    });
  }

  @Disabled
  @Test
  public void testDoNotDropCachesOnCodeBlockChange() {
    final Ref<Integer> counter = new Ref<>(0);

    runInEdtAndWait(() -> {
      AstTransformationSupport transformationSupport = new AstTransformationSupport(){

        @Override
        public void applyTransformation(@NotNull TransformationContext context) {
          counter.set(counter.get() + 1);
        }
      };
      AstTransformationSupport.EP_NAME.getPoint().registerExtension(transformationSupport, getFixture().getTestRootDisposable());
    });
    GroovyFile file =
      (GroovyFile)getFixture().addFileToProject("_.groovy", "class Super { def foo() { 1 } }");
    runInEdtAndWait(() -> {
      final GrTypeDefinition clazz = file.getTypeDefinitions()[0];

      Assertions.assertEquals(0, (int)counter.get());
      Assertions.assertEquals(6, clazz.getMethods().length);
      Assertions.assertEquals(1, ((GrMethod)clazz.getMethods()[0]).getBlock().getStatements().length);
      Assertions.assertEquals(1, (int)counter.get());

      WriteCommandAction.runWriteCommandAction(getFixture().getProject(),
           () -> ((GrMethod)clazz.getMethods()[0]).getBlock().getStatements()[0].delete());

      Assertions.assertEquals(1, (int)counter.get());
      Assertions.assertEquals(6, clazz.getMethods().length);
      Assertions.assertEquals(0, ((GrMethod)clazz.getMethods()[0]).getBlock().getStatements().length);
      Assertions.assertEquals(1, (int)counter.get());
    });
  }

  @Test
  public void testDropCachesOnNonPhysicalContextChange() {
    runInEdtAndWait(() -> {
      PsiFileFactory fileFactory = PsiFileFactory.getInstance(getFixture().getProject());
      GroovyFile file =
        (GroovyFile)fileFactory.createFileFromText("a.groovy", GroovyFileType.GROOVY_FILE_TYPE, "class Super { @Deprecated void foo(){} }");
      GrTypeDefinition superClass = file.getTypeDefinitions()[0];

      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getFixture().getProject());
      GrNewExpression expression =
        (GrNewExpression)factory.createExpressionFromText("new Super() { void foo(){} }", superClass);
      GrAnonymousClassDefinition subClass = expression.getAnonymousClassDefinition();

      Assertions.assertNotNull(AnnotationUtil.findAnnotationInHierarchy(subClass.getMethods()[0], Deprecated.class));
      superClass.getMethods()[0].getModifierList().getAnnotations()[0].delete();
      Assertions.assertNull(AnnotationUtil.findAnnotationInHierarchy(subClass.getMethods()[0], Deprecated.class));
    });
  }
}
