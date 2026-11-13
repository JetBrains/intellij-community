package org.jetbrains.plugins.groovy.lang.overriding;

import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.overriding.OverridingFixtureKt.overridingFixture;

@TestApplication
public class AllSuperMethodsTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture();
  private final TestFixture<OverridingFixture> overriding = overridingFixture(project, TestUtils.getAbsoluteTestDataPath() + "overriding/allSuperMethods");

  @Test
  public void testExtendsAndImplements() {
    overriding.get().doTest(AllSuperMethodsTest::findMethod);
  }
  @Test
  public void testExtendsClass() {
    overriding.get().doTest(AllSuperMethodsTest::findMethod);
  }
  @Test
  public void testImplementsImplements() {
    overriding.get().doTest(AllSuperMethodsTest::findMethod);
  }
  @Test
  public void testImplementsInterface() {
    overriding.get().doTest(AllSuperMethodsTest::findMethod);
  }
  @Test
  public void testManyOverrMethods2() {
    overriding.get().doTest(AllSuperMethodsTest::findMethod);
  }
  @Test
  public void testManySuperMethods() {
    overriding.get().doTest(AllSuperMethodsTest::findMethod);
  }
  @Test
  public void testMethodWithoutParameters() {
    overriding.get().doTest(AllSuperMethodsTest::findMethod);
  }
  @Test
  public void testMethodWithParameters() {
    overriding.get().doTest(AllSuperMethodsTest::findMethod);
  }

  private static PsiMethod[] findMethod(PsiMethod method) {
    return method.findSuperMethods();
  }
}
