package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.siyeh.ig.IGInspectionTestCase;

public class InterfaceNeverImplementedInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final LanguageLevelProjectExtension levelProjectExtension = LanguageLevelProjectExtension.getInstance(getProject());
    final LanguageLevel level = levelProjectExtension.getLanguageLevel();
    try {
      levelProjectExtension.setLanguageLevel(LanguageLevel.JDK_1_8);
      doTest("com/siyeh/igtest/inheritance/interface_never_implemented",
             new LocalInspectionToolWrapper(new InterfaceNeverImplementedInspection()), "java 1.8");
    }
    finally {
      levelProjectExtension.setLanguageLevel(level);
    }
  }

  @Override
  protected Sdk getTestProjectSdk() {
    return IdeaTestUtil.getMockJdk18();
  }
}
