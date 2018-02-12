package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.siyeh.ig.IGInspectionTestCase;

public class UnnecessaryReturnInspectionTest extends IGInspectionTestCase {

  public void test() {
    final UnnecessaryReturnInspection inspection = new UnnecessaryReturnInspection();
    inspection.ignoreInThenBranch = true;
    final LanguageLevelProjectExtension levelProjectExtension = LanguageLevelProjectExtension.getInstance(getProject());
    final LanguageLevel level = levelProjectExtension.getLanguageLevel();
    try {
      levelProjectExtension.setLanguageLevel(LanguageLevel.JDK_1_8);
      doTest("com/siyeh/igtest/controlflow/unnecessary_return", new LocalInspectionToolWrapper(inspection), "java 1.8");
    }
    finally {
      levelProjectExtension.setLanguageLevel(level);
    }
  }
}
