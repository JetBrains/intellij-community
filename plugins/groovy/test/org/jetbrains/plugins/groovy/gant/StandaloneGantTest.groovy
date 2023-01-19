// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.gant

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.util.SdkHomeBean
import org.jetbrains.plugins.groovy.util.TestUtils

class StandaloneGantTest extends LightJavaCodeInsightFixtureTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_1_7
  }

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "gant/completion"
  }

  @Override protected void setUp() {
    super.setUp()
    final SdkHomeBean state = new SdkHomeBean()
    state.setSdkHome(FileUtil.toSystemIndependentName("${TestUtils.absoluteTestDataPath}mockGantLib"))
    GantSettings.getInstance(getProject()).loadState state
  }

  @Override protected void tearDown() {
    try {
      Project project = getProject()
      if (project != null) {
        GantSettings.getInstance(project).loadState new SdkHomeBean()
      }
    }
    finally {
      super.tearDown()
    }
  }

  void checkVariants(String text, String... items) {
    myFixture.configureByText "a.gant", text
    myFixture.completeBasic()
    assertSameElements myFixture.lookupElementStrings, items
  }

  void testDep() throws Throwable {
    checkVariants """
target(aaa: "") {
    depend<caret>
}
""", "depends", "dependset"
  }

  void testPatternset() throws Exception {
    checkVariants "ant.patt<caret>t", "patternset"
  }

  void testOptionalArgumentsHighlighting() throws Exception {
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

  void testPathElement() throws Exception {
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

