// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NonNls;

public abstract class EditingTestBase extends AbstractEditorTest {
  protected CodeStyleSettings mySettings;
  private EditorColorsScheme mySavedScheme;
  private EditorColorsScheme myTestScheme;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mySettings = CodeStyle.createTestSettings(CodeStyle.getSettings(getProject()));
    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(mySettings);

    mySavedScheme = EditorColorsManager.getInstance().getGlobalScheme();
    myTestScheme = (EditorColorsScheme)mySavedScheme.clone();
    myTestScheme.setName("EditingTest.testScheme");
    EditorColorsManager.getInstance().addColorScheme(myTestScheme);
    EditorColorsManager.getInstance().setGlobalScheme(myTestScheme);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      mySettings = null;
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();

      EditorColorsManager.getInstance().setGlobalScheme(mySavedScheme);
      ((EditorColorsManagerImpl)EditorColorsManager.getInstance()).getSchemeManager().removeScheme(myTestScheme);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected void doTest(@NonNls FileType fileType, final char ... chars) {
    doTest(fileType, () -> {
      for (char c : chars) {
        type(c);
      }
    });
  }

  protected void doTest(@NonNls FileType fileType, Runnable action) {
    CommonCodeStyleSettings.IndentOptions indentOptions = mySettings.getIndentOptions(null);
    indentOptions.USE_TAB_CHARACTER = true;
    indentOptions.INDENT_SIZE = 4;
    indentOptions.TAB_SIZE = 4;
    indentOptions.CONTINUATION_INDENT_SIZE = 4;
    UISettings.getInstance().setFontFace("Tahoma");
    UISettings.getInstance().setFontSize(11);

    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    scheme.setEditorFontName("Tahoma");
    scheme.setEditorFontSize(11);

    String path = "/codeInsight/editing/before" + getTestName(false) + "."+fileType.getDefaultExtension();
    configureByFile(path);
    assertFileTypeResolved(fileType, path);
    action.run();
    checkResultByFile("/codeInsight/editing/after" + getTestName(false) + "."+fileType.getDefaultExtension());
  }
}
