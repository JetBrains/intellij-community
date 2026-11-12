package org.jetbrains.plugins.groovy.lang.overriding;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.lang.overriding.OverridingFixture.overridingFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;

@TestApplication
public class DeepestSuperMethodsTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(project, LightJavaCodeInsightFixtureTestCase.JAVA_LATEST, "overriding/deepestSuperMethods");
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
