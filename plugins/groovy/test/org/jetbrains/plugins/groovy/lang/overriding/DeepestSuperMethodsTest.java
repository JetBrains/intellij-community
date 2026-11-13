package org.jetbrains.plugins.groovy.lang.overriding;

import com.intellij.psi.PsiMethod;
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
public class DeepestSuperMethodsTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project, "overriding/deepestSuperMethods");
  private final TestFixture<OverridingTestFixture> overriding = overridingFixture(groovy);

  @Test
  public void testManyOverridingMethods() { runInEdtAndWait(() -> { overriding.get().doTest(DeepestSuperMethodsTest::findMethod); }); }
  @Test
  public void testManyOverrMethods2() { runInEdtAndWait(() -> { overriding.get().doTest(DeepestSuperMethodsTest::findMethod); }); }
  @Test
  public void testManyOverrMethods3() { runInEdtAndWait(() -> { overriding.get().doTest(DeepestSuperMethodsTest::findMethod); }); }
  @Test
  public void testManyOverrMethods4() { runInEdtAndWait(() -> { overriding.get().doTest(DeepestSuperMethodsTest::findMethod); }); }
  @Test
  public void testOverrideClassMethod() { runInEdtAndWait(() -> { overriding.get().doTest(DeepestSuperMethodsTest::findMethod); }); }
  @Test
  public void testOverrideInterfaceMethod() { runInEdtAndWait(() -> { overriding.get().doTest(DeepestSuperMethodsTest::findMethod); }); }


  private static PsiMethod[] findMethod(PsiMethod method) {
    return method.findDeepestSuperMethods();
  }

}
