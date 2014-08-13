package com.siyeh.ig.style;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.siyeh.ig.IGInspectionTestCase;

public class UnnecessaryFullyQualifiedNameInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(getProject());

    boolean inJavadoc = styleSettings.USE_FQ_CLASS_NAMES_IN_JAVADOC;
    try {
      styleSettings.USE_FQ_CLASS_NAMES_IN_JAVADOC = false;
      doTest("com/siyeh/igtest/style/unnecessary_fully_qualified_name",
             new UnnecessaryFullyQualifiedNameInspection());
    }
    finally {
      styleSettings.USE_FQ_CLASS_NAMES_IN_JAVADOC = inJavadoc;
    }
  }
}
