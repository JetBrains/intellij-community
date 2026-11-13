package org.jetbrains.plugins.groovy.lang.overriding;

import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.overriding.OverridingFixture.overridingFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class AllSuperMethodsTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project, "overriding/allSuperMethods");
  private final TestFixture<OverridingTestFixture> overriding = overridingFixture(groovy);

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
