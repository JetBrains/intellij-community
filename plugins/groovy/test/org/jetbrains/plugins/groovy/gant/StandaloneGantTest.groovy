package org.jetbrains.plugins.groovy.gant;


import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.gant.GantSettings
import org.jetbrains.plugins.groovy.util.SdkHomeConfigurable.SdkHomeBean
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author peter
 */
public class StandaloneGantTest extends LightCodeInsightFixtureTestCase {

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
    dep<caret>
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

  static final def GANT_JARS = ["gant.jar", "ant.jar", "ant-junit.jar", "ant-launcher.jar", "commons.jar"]

}

