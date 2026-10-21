// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.editorconfig.common.EditorConfigBundle;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.editorconfig.EditorConfigRegistry;
import org.editorconfig.Utils;
import org.editorconfig.settings.EditorConfigSettings;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class EditorConfigEncodingInspectionTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private CodeInsightTestFixture myFixture;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    myFixture = codeInsightFixture.get();
    String testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), true);
    myFixture.setTestDataPath(PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') +
                              "/plugins/editorconfig/testData/org/editorconfig/configmanagement/inspections/" + testName);
    EditorConfigRegistry.setSkipProjectRootInTest(true);
    Utils.setEnabledInTests(true);
  }

  @AfterEach
  void tearDown() {
    try {
      Utils.setEnabledInTests(false);
    }
    finally {
      EditorConfigRegistry.setSkipProjectRootInTest(false);
    }
  }

  @Test
  public void testEncodingMismatch() {
    doWithEditorConfigDisabled(()->myFixture.configureByFile("test.txt"));
    myFixture.copyFileToProject(".editorconfig");
    myFixture.enableInspections(EditorConfigEncodingInspection.class);
    myFixture.checkHighlighting();
  }

  @Test
  public void testIgnoreHardcodedCharset() {
    doWithEditorConfigDisabled(()->myFixture.configureByFile("test.xml"));
    myFixture.copyFileToProject(".editorconfig");
    myFixture.enableInspections(EditorConfigEncodingInspection.class);
    myFixture.checkHighlighting();
  }

  @Test
  public void testIgnoreQuickFix() {
    doWithEditorConfigDisabled(()->myFixture.configureByFile("test.txt"));
    myFixture.copyFileToProject(".editorconfig");
    myFixture.enableInspections(EditorConfigEncodingInspection.class);
    IntentionAction quickFix = myFixture.findSingleIntention(EditorConfigBundle.message("inspection.file.encoding.ignore"));
    assertNotNull(quickFix);
    myFixture.launchAction(quickFix);
    assertTrue(EditorConfigEncodingCache.Companion.getInstance().isIgnored(myFixture.getFile().getVirtualFile()));
    myFixture.checkHighlighting();
  }

  @Test
  public void testApplyEncodingQuickFix() {
    doWithEditorConfigDisabled(()->myFixture.configureByFile("test.txt"));
    myFixture.copyFileToProject(".editorconfig");
    VirtualFile sourceFile = myFixture.getFile().getVirtualFile();
    assertEquals(StandardCharsets.UTF_8, sourceFile.getCharset());
    myFixture.enableInspections(EditorConfigEncodingInspection.class);
    IntentionAction quickFix = myFixture.findSingleIntention(EditorConfigBundle.message("inspection.file.encoding.apply"));
    assertNotNull(quickFix);
    myFixture.launchAction(quickFix);
    assertEquals(StandardCharsets.ISO_8859_1, sourceFile.getCharset());
    myFixture.checkHighlighting();
  }

  private void doWithEditorConfigDisabled(@NotNull Runnable runnable) {
    EditorConfigSettings settings = CodeStyle.getSettings(myFixture.getProject()).getCustomSettings(EditorConfigSettings.class);
    settings.ENABLED = false;
    try {
      runnable.run();
    }
    finally {
      settings.ENABLED = true;
    }
  }
}
