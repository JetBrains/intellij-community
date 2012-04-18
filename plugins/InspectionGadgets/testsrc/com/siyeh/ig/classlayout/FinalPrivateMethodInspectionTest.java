package com.siyeh.ig.classlayout;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.siyeh.ig.IGInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class FinalPrivateMethodInspectionTest extends IGInspectionTestCase {

  @Override
  protected Sdk getTestProjectSdk() {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
    return JavaSdkImpl.getMockJdk17();
  }

  public void test() throws Exception {
    doTest("com/siyeh/igtest/classlayout/final_private_method", new FinalPrivateMethodInspection());
  }
}
