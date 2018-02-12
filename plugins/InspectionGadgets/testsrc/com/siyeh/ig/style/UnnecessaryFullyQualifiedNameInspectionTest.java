package com.siyeh.ig.style;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.IdeaTestUtil;
import com.siyeh.ig.IGInspectionTestCase;

public class UnnecessaryFullyQualifiedNameInspectionTest extends IGInspectionTestCase {
  private static final String BASE_DIR = "com/siyeh/igtest/style/";

  @Override
  protected Sdk getTestProjectSdk() {
    // uses awt
    return IdeaTestUtil.getMockJdk17();
  }

  public void testFqnInJavadoc_Unnecessary_WhenFullyQualifyIfNotImported() {
    doTestWithFqnInJavadocSetting(BASE_DIR + "unnecessary_fqn_javadoc_fully_qualify_if_not_imported", JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED);
  }

  public void testFqnInJavadoc_Unnecessary_WhenShortNamesAlways() {
    doTestWithFqnInJavadocSetting(BASE_DIR + "unnecessary_fully_qualified_name/", JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT);
  }

  public void testAcceptFqnInJavadoc() {
    doTestWithFqnInJavadocSetting(BASE_DIR + "unnecessary_fully_qualified_name_accept_in_javadoc/", JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS);
  }

  public void testConflictWithTypeParameter() {
    doTest(BASE_DIR + "unnecessary_fqn_type_parameter_conflict", new UnnecessaryFullyQualifiedNameInspection());
  }

  private void doTestWithFqnInJavadocSetting(String dirPath, int classNamesInJavadoc) {
    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);

    int oldClassNamesInJavadoc = javaSettings.CLASS_NAMES_IN_JAVADOC;
    try {
      javaSettings.CLASS_NAMES_IN_JAVADOC = classNamesInJavadoc;
      doTest(dirPath, new UnnecessaryFullyQualifiedNameInspection());
    }
    finally {
      javaSettings.CLASS_NAMES_IN_JAVADOC = oldClassNamesInJavadoc;
    }
  }
}
