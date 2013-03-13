package com.siyeh.ig.threading;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.siyeh.ig.IGInspectionTestCase;

public class SynchronizationOnLocalVariableOrMethodParameterInspectionTest extends IGInspectionTestCase {

  @Override
  protected Sdk getTestProjectSdk() {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_8);
    return IdeaTestUtil.getMockJdk17();
  }

  public void test() throws Exception {
    doTest("com/siyeh/igtest/threading/synchronization_on_local_variable_or_method_parameter",
           new SynchronizationOnLocalVariableOrMethodParameterInspection());
  }
}
