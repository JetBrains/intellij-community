package org.jetbrains.plugins.groovy.refactoring.convertJavaToGroovy;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import junit.framework.TestCase;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.impl.AnnotationArgConverter;

/**
 * Created by Max Medvedev on 8/19/13
 */
public class ConvertAnnotationMemberValueTest extends LightJavaCodeInsightFixtureTestCase {
  public void testSimpleExpression1() {
    doTest("1 + 1", "1 + 1");
  }

  public void testSimpleExpression2() {
    doTest("foo(bar)", "foo(bar)");
  }

  public void testSimpleAnnotation1() {
    doTest("@A", "@A");
  }

  public void testSimpleAnnotation2() {
    doTest("@A()", "@A");
  }

  public void testSimpleAnnotation3() {
    doTest("@A(1, value = 2)", "@A(1,value=2)");
  }

  public void testAnnotationInitializer() {
    doTest("@A({1, 2, 3})", "@A([1,2,3])");
  }

  public void testArrayInitializer() {
    doTest("new int[]{1, 2, 3}", "([1,2,3] as int[])");
  }

  public void doTest(@NotNull String java, @NotNull String expectedGroovy) {
    myFixture.configureByText(JavaFileType.INSTANCE, "@A(" + java + ") class Clazz{}");

    PsiJavaFile file = DefaultGroovyMethods.asType(myFixture.getFile(), PsiJavaFile.class);
    PsiAnnotationMemberValue value =
      file.getClasses()[0].getModifierList().getAnnotations()[0].getParameterList().getAttributes()[0].getValue();
    GrAnnotationMemberValue result = new AnnotationArgConverter().convert(value);

    TestCase.assertEquals(expectedGroovy, result.getText());
  }
}
