package org.jetbrains.plugins.groovy.lang.overriding;

import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.lang.overriding.OverridingFixture.overridingFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class AllSuperMethodsTest {
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST, "overriding/allSuperMethods");
  private final TestFixture<OverridingTestFixture> overriding = overridingFixture(groovy);

  @Test
  public void testExtendsAndImplements() { runInEdtAndWait(() -> { overriding.get().doTest(AllSuperMethodsTest::findMethod); }); }
  @Test
  public void testExtendsClass() { runInEdtAndWait(() -> { overriding.get().doTest(AllSuperMethodsTest::findMethod); }); }
  @Test
  public void testImplementsImplements() { runInEdtAndWait(() -> { overriding.get().doTest(AllSuperMethodsTest::findMethod); }); }
  @Test
  public void testImplementsInterface() { runInEdtAndWait(() -> { overriding.get().doTest(AllSuperMethodsTest::findMethod); }); }
  @Test
  public void testManyOverrMethods2() { runInEdtAndWait(() -> { overriding.get().doTest(AllSuperMethodsTest::findMethod); }); }
  @Test
  public void testManySuperMethods() { runInEdtAndWait(() -> { overriding.get().doTest(AllSuperMethodsTest::findMethod); }); }
  @Test
  public void testMethodWithoutParameters() { runInEdtAndWait(() -> { overriding.get().doTest(AllSuperMethodsTest::findMethod); }); }
  @Test
  public void testMethodWithParameters() { runInEdtAndWait(() -> { overriding.get().doTest(AllSuperMethodsTest::findMethod); }); }

  private static PsiMethod[] findMethod(PsiMethod method) {
    return method.findSuperMethods();
  }
}
