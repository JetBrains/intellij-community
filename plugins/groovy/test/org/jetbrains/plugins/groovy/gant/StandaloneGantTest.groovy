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
package org.jetbrains.plugins.groovy.gant
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.util.SdkHomeBean
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author peter
 */
public class StandaloneGantTest extends LightCodeInsightFixtureTestCase {
  public static final DefaultLightProjectDescriptor GROOVY_17_PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      final Library.ModifiableModel modifiableModel = model.getModuleLibraryTable().createLibrary("GROOVY").getModifiableModel();
      final VirtualFile groovyJar = JarFileSystem.getInstance().refreshAndFindFileByPath(TestUtils.getMockGroovy1_7LibraryName() + "!/");
      modifiableModel.addRoot(groovyJar, OrderRootType.CLASSES);
      modifiableModel.commit();
    }
  };

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GROOVY_17_PROJECT_DESCRIPTOR;
  }

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "gant/completion";
  }

  @Override protected void setUp() {
    super.setUp();
    final SdkHomeBean state = new SdkHomeBean();
    state.SDK_HOME = FileUtil.toSystemIndependentName("${TestUtils.absoluteTestDataPath}mockGantLib");
    GantSettings.getInstance(getProject()).loadState state
  }

  @Override protected void tearDown() {
    GantSettings.getInstance(getProject()).loadState new SdkHomeBean()
    super.tearDown();
  }

  void checkVariants(String text, String... items) {
    myFixture.configureByText "a.gant", text
    myFixture.completeBasic()
    assertSameElements myFixture.lookupElementStrings, items
  }

  public void testDep() throws Throwable {
    checkVariants """
target(aaa: "") {
    depend<caret>
}
""", "depends", "dependset"
  }

  public void testPatternset() throws Exception {
    checkVariants "ant.patt<caret>t", "patternset"
  }

  public void testOptionalArgumentsHighlighting() throws Exception {
    myFixture.configureByText "a.gant", """
    ant.java(classname: "com.intellij.util.io.zip.ReorderJarsMain", fork: "true") {
      arg(value: "aaa")
      classpath {
        pathelement(location: "sss")
      }
    }
"""
    myFixture.checkHighlighting(true, false, false)
  }

  public void testPathElement() throws Exception {
    checkVariants """
    ant.java(classname: "com.intellij.util.io.zip.ReorderJarsMain", fork: "true") {
      arg(value: "aaa")
      classpath {
        pathele<caret>ment(location: "sss")
      }
    }
""", "pathelement"
  }

  static final def GANT_JARS = ["gant.jar", "ant.jar", "ant-junit.jar", "ant-launcher.jar", "commons.jar"]

}

