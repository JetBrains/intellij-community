package com.siyeh.ig.visibility;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.siyeh.ig.IGInspectionTestCase;

public class MethodOverloadsParentMethodInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/visibility/method_overloads_parent_method", new MethodOverloadsParentMethodInspection());
  }

  @Override
  protected Sdk getTestProjectSdk() {
    final Sdk sdk = IdeaTestUtil.getMockJdk17();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.HIGHEST);
    return sdk;
  }
  
}