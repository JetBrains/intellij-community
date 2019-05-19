package com.siyeh.ig.style;

import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.siyeh.ig.IGInspectionTestCase;

public class UnnecessaryFullyQualifiedNameInspectionTest extends IGInspectionTestCase {
  private static final String BASE_DIR = "com/siyeh/igtest/style/";

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
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());

    javaSettings.CLASS_NAMES_IN_JAVADOC = classNamesInJavadoc;
    doTest(dirPath, new UnnecessaryFullyQualifiedNameInspection());
  }
}
