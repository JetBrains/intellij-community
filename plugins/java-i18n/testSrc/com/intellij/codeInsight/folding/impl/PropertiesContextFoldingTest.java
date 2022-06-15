// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.lang.properties.PropertiesFoldingSettings;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class PropertiesContextFoldingTest extends BasePlatformTestCase {
  private boolean old;

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/plugins/java-i18n/testData/contextFolding";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    old = PropertiesFoldingSettings.getInstance().isFoldPlaceholdersToContext();
    PropertiesFoldingSettings.getInstance().setFoldPlaceholdersToContext(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      PropertiesFoldingSettings.getInstance().setFoldPlaceholdersToContext(old);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testPlaceholdersInPropertiesAreFolded() throws IOException {
    myFixture.configureByFile("MyClass.java");
    myFixture.configureByFile("i18n.properties");

    String actual = StringUtil.convertLineSeparators(((CodeInsightTestFixtureImpl)myFixture).getFoldingDescription(true));
    String expected = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(myFixture.getTestDataPath(), "i18n.after")));

    assertEquals(expected, actual);
  }

  public void testRangesInJava() {
    JavaCodeFoldingSettings foldingSettings = JavaCodeFoldingSettings.getInstance();
    boolean collapseMethods = foldingSettings.isCollapseMethods();
    boolean i18nMessages = foldingSettings.isCollapseI18nMessages();
    try {
      foldingSettings.setCollapseMethods(false);
      foldingSettings.setCollapseI18nMessages(true);
      myFixture.copyFileToProject("i18n.properties");
      PsiFile file = myFixture.configureByFile("MyClass1.java");
      
      List<FoldingUpdate.RegionInfo> foldings = FoldingUpdate.getFoldingsFor(file, false);
      assertFalse(foldings.stream().map(info -> info.descriptor).anyMatch(descriptor -> descriptor.getPlaceholderText().equals("\"Welcome\"")));
    }
    finally {
      foldingSettings.setCollapseMethods(collapseMethods);
      foldingSettings.setCollapseI18nMessages(i18nMessages);
    }
  }
}