// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.impl.InvokeTemplateAction;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.util.HashSet;
import java.util.List;

public abstract class MarkupSurroundTestBase extends BasePlatformTestCase {
  protected static final String BASE_PATH = "/codeInsight/surroundWith/";

  private List<InvokeTemplateAction> buildSurroundersForFileTypeWithGivenExtension() {
    return ContainerUtil.map(
      TemplateManagerImpl.listApplicableTemplateWithInsertingDummyIdentifier(
        TemplateActionContext.surrounding(myFixture.getFile(), myFixture.getEditor())),
      template -> new InvokeTemplateAction(template, myFixture.getEditor(), getProject(), new HashSet<>()));
  }

  protected void doSurroundWithTagTest(String ext) {
    String baseName = getBaseName("tag");
    myFixture.configureByFile(baseName + "." + ext);
    List<InvokeTemplateAction> actions = buildSurroundersForFileTypeWithGivenExtension();
    actions.get(0).perform();
    myFixture.checkResultByFile(baseName + "_after." + ext);
  }

  protected void doSurroundWithCDataTest(String ext) {
    String baseName = getBaseName("");
    myFixture.configureByFile(baseName + "." + ext);
    buildSurroundersForFileTypeWithGivenExtension().get(1).perform();
    myFixture.checkResultByFile(baseName + "_after." + ext);
  }

  protected String getBaseName(String dir) {
    String baseName = BASE_PATH;
    if (!dir.isEmpty()) baseName += dir + "/";
    baseName += getTestName(false);
    return baseName;
  }
}
