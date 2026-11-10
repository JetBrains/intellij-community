// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.util.SdkHomeBean;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class StandaloneGantTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_1_7);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "gant/completion");
    final SdkHomeBean state = new SdkHomeBean();
    var sdkHome = TestUtils.getAbsoluteTestDataPath() + "mockGantLib";
    VfsRootAccess.allowRootAccess(myFixture.getTestRootDisposable(), sdkHome);
    state.setSdkHome(FileUtil.toSystemIndependentName(sdkHome));
    EdtTestUtil.runInEdtAndWait(() -> GantSettings.getInstance(myFixture.getProject()).loadState(state));
  }

  @AfterEach
  void tearDown() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Project project = myFixture.getProject();
    if (project != null) {
      EdtTestUtil.runInEdtAndWait(() -> GantSettings.getInstance(project).loadState(new SdkHomeBean()));
    }
  }

  public void checkVariants(String text, String... items) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.gant", text);
    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).containsOnly(items);
  }

  @Test
  public void testDep() {
    checkVariants("""
                    target(aaa: "") {
                        depend<caret>
                    }
                    """, "depends", "dependset");
  }

  @Test
  public void testPatternset() {
    checkVariants("ant.patt<caret>t", "patternset");
  }
}
