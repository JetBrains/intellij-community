package org.jetbrains.plugins.groovy.refactoring.convertJavaToGroovy;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.impl.AnnotationArgConverter;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Created by Max Medvedev on 8/19/13
 */
@TestApplication
public class ConvertAnnotationMemberValueTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testSimpleExpression1() {
    doTest("1 + 1", "1 + 1");
  }

  @Test
  public void testSimpleExpression2() {
    doTest("foo(bar)", "foo(bar)");
  }

  @Test
  public void testSimpleAnnotation1() {
    doTest("@A", "@A");
  }

  @Test
  public void testSimpleAnnotation2() {
    doTest("@A()", "@A");
  }

  @Test
  public void testSimpleAnnotation3() {
    doTest("@A(1, value = 2)", "@A(1,value=2)");
  }

  @Test
  public void testAnnotationInitializer() {
    doTest("@A({1, 2, 3})", "@A([1,2,3])");
  }

  @Test
  public void testArrayInitializer() {
    doTest("new int[]{1, 2, 3}", "([1,2,3] as int[])");
  }

  public void doTest(@NotNull String java, @NotNull String expectedGroovy) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(JavaFileType.INSTANCE, "@A(" + java + ") class Clazz{}");

    ReadAction.runBlocking(() -> {
      PsiJavaFile file = DefaultGroovyMethods.asType(myFixture.getFile(), PsiJavaFile.class);
      PsiAnnotationMemberValue value =
        file.getClasses()[0].getModifierList().getAnnotations()[0].getParameterList().getAttributes()[0].getValue();
      GrAnnotationMemberValue result = new AnnotationArgConverter().convert(value);

      assertEquals(expectedGroovy, result.getText());
    });
  }
}
