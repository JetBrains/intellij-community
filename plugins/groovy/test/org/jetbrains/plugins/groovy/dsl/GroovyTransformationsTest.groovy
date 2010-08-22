package org.jetbrains.plugins.groovy.dsl

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author ilyas
 */
class GroovyTransformationsTest extends LightCodeInsightFixtureTestCase {
  static def descriptor = new DefaultLightProjectDescriptor() {
    @Override def void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
      PsiTestUtil.addLibrary(module, model, "GROOVY", TestUtils.getMockGroovy1_7LibraryHome(), TestUtils.GROOVY_JAR_17);
    }
  }

  @Override
  protected String getBasePath() {
    TestUtils.getTestDataPath() + "groovy/dsl/transform"
  }

  public void doPlainTest() throws Throwable {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy")
  }

  public void doVariantsTest(String ... variants) throws Throwable {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", variants)
  }

  @Override protected LightProjectDescriptor getProjectDescriptor() {
    return descriptor;
  }

  public void testDelegateAnnotation() throws Throwable { doPlainTest() }

  public void testSingletonTransform() throws Throwable { doPlainTest() }

  public void testCategoryTransform() throws Throwable { doPlainTest() }

  public void testMixinTransform() throws Throwable { doPlainTest() }

  public void testBindableTransform() throws Throwable { doPlainTest() }

  public void testVetoableTransform() throws Throwable { doPlainTest() }

  public void testNewifyTransform1() throws Throwable { doVariantsTest('newInstance', 'new', 'new', 'new',
                                                                       'newInstance', 'newInstance0', 'newInstanceCallerCache', 'next') }

  public void testNewifyTransform2() throws Throwable { doVariantsTest('Leaf', 'Leaf', 'Leaf') }

  public void testNewifyTransform3() throws Throwable { doVariantsTest('Bazz', 'Bazz') }

}
