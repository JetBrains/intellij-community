// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.util.SdkHomeBean;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class StandaloneGantTest extends LightJavaCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_1_7;
  }

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "gant/completion";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final SdkHomeBean state = new SdkHomeBean();
    state.setSdkHome(FileUtil.toSystemIndependentName(TestUtils.getAbsoluteTestDataPath() + "mockGantLib"));
    GantSettings.getInstance(getProject()).loadState(state);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Project project = getProject();
      if (project != null) {
        GantSettings.getInstance(project).loadState(new SdkHomeBean());
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void checkVariants(String text, String... items) {
    myFixture.configureByText("a.gant", text);
    myFixture.completeBasic();
    assertSameElements(myFixture.getLookupElementStrings(), items);
  }

  public void testDep() {
    checkVariants("""
                    target(aaa: "") {
                        depend<caret>
                    }
                    """, "depends", "dependset");
  }

  public void testPatternset() {
    checkVariants("ant.patt<caret>t", "patternset");
  }

  public void testOptionalArgumentsHighlighting() {
    myFixture.configureByText("a.gant", """
      ant.java(classname: "com.intellij.util.io.zip.ReorderJarsMain", fork: "true") {
        arg(value: "aaa")
        classpath {
          pathelement(location: "sss")
        }
      }
      """);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testPathElement() {
    checkVariants("""
                    ant.java(classname: "com.intellij.util.io.zip.ReorderJarsMain", fork: "true") {
                      arg(value: "aaa")
                      classpath {
                        pathele<caret>ment(location: "sss")
                      }
                    }
                    """, "pathelement");
  }
}