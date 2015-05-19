package com.siyeh.ig.inheritance;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.siyeh.ig.IGInspectionTestCase;

public class AbstractMethodOverridesAbstractMethodInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final AbstractMethodOverridesAbstractMethodInspection tool = new AbstractMethodOverridesAbstractMethodInspection();
    tool.ignoreAnnotations = true;
    tool.ignoreJavaDoc = true;
    doTest("com/siyeh/igtest/inheritance/abstract_method_overrides_abstract_method", tool);
  }

  @Override
  protected Sdk getTestProjectSdk() {
    Sdk sdk = IdeaTestUtil.getMockJdk17();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.HIGHEST);
    return sdk;
  }
}
