package org.jetbrains.plugins.groovy.dsl

import com.intellij.psi.PsiFile
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import org.jetbrains.plugins.groovy.lang.completion.CompletionTestBase
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author ilyas
 */
class GroovyTransformationsTest extends CompletionTestBase {

  @Override
  protected String getBasePath() {
    "/svnPlugins/groovy/testdata/groovy/dsl/transform"
  }

  public void doPlainTest() throws Throwable {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy")
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibraryJars("GROOVY", TestUtils.getMockGroovy1_7LibraryHome(), TestUtils.GROOVY_JAR_17);
  }

  public void testDelegateAnnotation() throws Throwable { doPlainTest() }

  public void testSingletonTransform() throws Throwable { doPlainTest() }

}
