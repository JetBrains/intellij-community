/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.dsl
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author ilyas
 */
class GroovyTransformationsTest extends LightCodeInsightFixtureTestCase {
  static def descriptor = new DefaultLightProjectDescriptor() {
    @Override def void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      PsiTestUtil.addLibrary(module, model, "GROOVY", TestUtils.getMockGroovy1_7LibraryHome(), TestUtils.GROOVY_JAR_17);
    }
  }

  @Override
  protected String getBasePath() {
    TestUtils.getTestDataPath() + "groovy/dsl/transform"
  }

  public void doPlainTest(String type = "") throws Throwable {
    myFixture.testCompletionTyping(getTestName(false) + ".groovy", type, getTestName(false) + "_after.groovy")
  }

  public void doVariantsTest(String ... variants) throws Throwable {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", variants)
  }

  @NotNull
  @Override protected LightProjectDescriptor getProjectDescriptor() {
    return descriptor;
  }

  public void testDelegateAnnotation() throws Throwable { doPlainTest() }

  public void testSingletonTransform() throws Throwable { doVariantsTest('instance', 'newInstance', 'newInstance', 'isInstance', 'getInstance', 'setInstance') }

  public void testCategoryTransform() throws Throwable { doVariantsTest('name', 'getName') }

  public void testMixinTransform() throws Throwable { doPlainTest() }

  public void testBindableTransform() throws Throwable { doPlainTest() }

  public void testVetoableTransform() throws Throwable { doPlainTest() }

  public void testNewifyTransform1() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy")
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings.containsAll(['newInstance', 'new', 'new', 'newInstance'])
  }

  public void testNewifyTransform2() throws Throwable { doVariantsTest('Leaf', 'Leaf', 'Leaf', 'Boolean') }

  public void testNewifyTransform3() throws Throwable { doVariantsTest('Bazz', 'Bazz') }

}
