package com.siyeh.ig.migration;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.siyeh.ig.IGInspectionTestCase;

public class TryFinallyCanBeTryWithResourcesInspectionTest extends IGInspectionTestCase {

  @Override
  protected Sdk getTestProjectSdk() {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
    return JavaSdkImpl.getMockJdk17();
  }

  public void test() throws Exception {
    doTest("com/siyeh/igtest/migration/try_finally_can_be_try_with_resources", new TryFinallyCanBeTryWithResourcesInspection());
  }
}
