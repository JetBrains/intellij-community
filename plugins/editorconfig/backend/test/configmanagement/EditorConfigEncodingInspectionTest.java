// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.editorconfig.common.EditorConfigBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.InspectionFixtureTestCase;
import org.editorconfig.EditorConfigRegistry;
import org.editorconfig.Utils;
import org.editorconfig.settings.EditorConfigSettings;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

public class EditorConfigEncodingInspectionTest extends InspectionFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    EditorConfigRegistry.setSkipProjectRootInTest(true);
    Utils.setEnabledInTests(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Utils.setEnabledInTests(false);
      EditorConfigRegistry.setSkipProjectRootInTest(false);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testEncodingMismatch() {
    doWithEditorConfigDisabled(()->myFixture.configureByFile("test.txt"));
    myFixture.copyFileToProject(".editorconfig");
    myFixture.enableInspections(EditorConfigEncodingInspection.class);
    myFixture.checkHighlighting();
  }

  public void testIgnoreHardcodedCharset() {
    doWithEditorConfigDisabled(()->myFixture.configureByFile("test.xml"));
    myFixture.copyFileToProject(".editorconfig");
    myFixture.enableInspections(EditorConfigEncodingInspection.class);
    myFixture.checkHighlighting();
  }

  public void testIgnoreQuickFix() {
    doWithEditorConfigDisabled(()->myFixture.configureByFile("test.txt"));
    myFixture.copyFileToProject(".editorconfig");
    myFixture.enableInspections(EditorConfigEncodingInspection.class);
    IntentionAction quickFix = myFixture.findSingleIntention(EditorConfigBundle.message("inspection.file.encoding.ignore"));
    assertNotNull(quickFix);
    myFixture.launchAction(quickFix);
    assertTrue(EditorConfigEncodingCache.Companion.getInstance().isIgnored(getFile().getVirtualFile()));
    myFixture.checkHighlighting();
  }

  public void testApplyEncodingQuickFix() {
    doWithEditorConfigDisabled(()->myFixture.configureByFile("test.txt"));
    myFixture.copyFileToProject(".editorconfig");
    VirtualFile sourceFile = getFile().getVirtualFile();
    assertEquals(StandardCharsets.UTF_8, sourceFile.getCharset());
    myFixture.enableInspections(EditorConfigEncodingInspection.class);
    IntentionAction quickFix = myFixture.findSingleIntention(EditorConfigBundle.message("inspection.file.encoding.apply"));
    assertNotNull(quickFix);
    myFixture.launchAction(quickFix);
    assertEquals(StandardCharsets.ISO_8859_1, sourceFile.getCharset());
    myFixture.checkHighlighting();
  }

  private void doWithEditorConfigDisabled(@NotNull Runnable runnable) {
    EditorConfigSettings settings = CodeStyle.getSettings(getProject()).getCustomSettings(EditorConfigSettings.class);
    settings.ENABLED = false;
    try {
      runnable.run();
    }
    finally {
      settings.ENABLED = true;
    }
  }

  @Override
  protected String getBasePath() {
    return "/plugins/editorconfig/testData/org/editorconfig/configmanagement/inspections/" + getTestName(true);
  }

  @Override
  protected boolean isCommunity() {
    return true;
  }
}
