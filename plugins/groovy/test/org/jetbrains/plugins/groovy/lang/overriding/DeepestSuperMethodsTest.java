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
public class DeepestSuperMethodsTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture();
  private final TestFixture<OverridingFixture> overriding = overridingFixture(project, TestUtils.getAbsoluteTestDataPath() + "overriding/deepestSuperMethods");

  @Test
  public void testManyOverridingMethods() {
    overriding.get().doTest(DeepestSuperMethodsTest::findMethod);
  }
  @Test
  public void testManyOverrMethods2() {
    overriding.get().doTest(DeepestSuperMethodsTest::findMethod);
  }
  @Test
  public void testManyOverrMethods3() {
    overriding.get().doTest(DeepestSuperMethodsTest::findMethod);
  }
  @Test
  public void testManyOverrMethods4() {
    overriding.get().doTest(DeepestSuperMethodsTest::findMethod);
  }
  @Test
  public void testOverrideClassMethod() {
    overriding.get().doTest(DeepestSuperMethodsTest::findMethod);
  }
  @Test
  public void testOverrideInterfaceMethod() {
    overriding.get().doTest(DeepestSuperMethodsTest::findMethod);
  }


  private static PsiMethod[] findMethod(PsiMethod method) {
    return method.findDeepestSuperMethods();
  }

}
