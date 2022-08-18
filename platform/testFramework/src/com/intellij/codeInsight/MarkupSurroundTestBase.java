// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.impl.InvokeTemplateAction;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.util.HashSet;
import java.util.List;

public abstract class MarkupSurroundTestBase extends LightPlatformCodeInsightTestCase {
  protected static final String BASE_PATH = "/codeInsight/surroundWith/";

  private List<InvokeTemplateAction> buildSurroundersForFileTypeWithGivenExtension() {
    return ContainerUtil.map(
      TemplateManagerImpl.listApplicableTemplateWithInsertingDummyIdentifier(
        TemplateActionContext.surrounding(getFile(), getEditor())),
      template -> new InvokeTemplateAction(template, getEditor(), getProject(), new HashSet<>()));
  }

  protected void doSurroundWithTagTest(String ext) {
    String baseName = getBaseName("tag");
    configureByFile(baseName + "." + ext);
    List<InvokeTemplateAction> actions = buildSurroundersForFileTypeWithGivenExtension();
    actions.get(0).perform();
    checkResultByFile(baseName + "_after." + ext);
  }

  protected void doSurroundWithCDataTest(String ext) {
    String baseName = getBaseName("");
    configureByFile(baseName + "." + ext);
    buildSurroundersForFileTypeWithGivenExtension().get(1).perform();
    checkResultByFile(baseName + "_after." + ext);
  }

  private String getBaseName(String dir) {
    String baseName = BASE_PATH;
    if (!dir.isEmpty()) baseName += dir + "/";
    baseName += getTestName(false);
    return baseName;
  }
}
