// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.editorconfig.Utils;

public class EditorConfigStandardSettingsTest extends EditorConfigFileSettingsTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Utils.setFullIntellijSettingsSupportEnabledInTest(false);
  }

  public void testIndentSizeTab() {
    final CommonCodeStyleSettings.IndentOptions projectIndentOptions =
      CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE).getIndentOptions();
    projectIndentOptions.TAB_SIZE = 4;
    projectIndentOptions.INDENT_SIZE = 2;
    PsiFile javaFile = findPsiFile("source.java");
    final CommonCodeStyleSettings.IndentOptions indentOptions = CodeStyle.getIndentOptions(javaFile);
    assertTrue(indentOptions.USE_TAB_CHARACTER);
    assertEquals("Indent size doesn't match tab size", indentOptions.TAB_SIZE, indentOptions.INDENT_SIZE);
  }

  public void testIndentStyleTab() {
    final CommonCodeStyleSettings.IndentOptions projectIndentOptions =
      CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE).getIndentOptions();
    projectIndentOptions.TAB_SIZE = 4;
    projectIndentOptions.INDENT_SIZE = 2;
    PsiFile javaFile = findPsiFile("source.java");
    final CommonCodeStyleSettings.IndentOptions indentOptions = CodeStyle.getIndentOptions(javaFile);
    assertEquals("Indent size doesn't match tab size", indentOptions.TAB_SIZE, indentOptions.INDENT_SIZE);
  }

  public void testIndentSizeTabWidth() {
    PsiFile javaFile = findPsiFile("source.java");
    final CommonCodeStyleSettings.IndentOptions indentOptions = CodeStyle.getIndentOptions(javaFile);
    assertEquals(3, indentOptions.TAB_SIZE);
    assertEquals(3, indentOptions.INDENT_SIZE);
  }

  @Override
  protected String getRelativePath() {
    return "/plugins/editorconfig/testData/org/editorconfig/configmanagement/fileSettings";
  }
}
