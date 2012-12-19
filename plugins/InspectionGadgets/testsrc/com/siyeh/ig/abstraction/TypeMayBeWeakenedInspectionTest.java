package com.siyeh.ig.abstraction;

import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.siyeh.ig.IGInspectionTestCase;

public class TypeMayBeWeakenedInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final TypeMayBeWeakenedInspection inspection =
      new TypeMayBeWeakenedInspection();
    inspection.doNotWeakenToJavaLangObject = false;
    inspection.onlyWeakentoInterface = false;
    final LanguageLevelProjectExtension levelProjectExtension = LanguageLevelProjectExtension.getInstance(getProject());
    final LanguageLevel level = levelProjectExtension.getLanguageLevel();
    try {
      levelProjectExtension.setLanguageLevel(LanguageLevel.JDK_1_7);
      doTest("com/siyeh/igtest/abstraction/weaken_type", inspection);
    }
    finally {
      levelProjectExtension.setLanguageLevel(level);
    }
  }
}