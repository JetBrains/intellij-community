// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.application.options.CodeStyle;
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
    PsiFile javaFile = findPsiFile("source.java");
    final CommonCodeStyleSettings.IndentOptions indentOptions = CodeStyle.getIndentOptions(javaFile);
    assertTrue("Expected Tab character for indentation", indentOptions.USE_TAB_CHARACTER);
  }

  @Override
  protected String getRelativePath() {
    return "/plugins/editorconfig/testData/org/editorconfig/configmanagement/fileSettings";
  }
}
