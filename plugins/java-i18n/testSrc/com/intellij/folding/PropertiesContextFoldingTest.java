// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.folding;

import com.intellij.lang.properties.PropertiesFoldingSettings;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;

import java.io.File;
import java.io.IOException;

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
    PropertiesFoldingSettings.getInstance().setFoldPlaceholdersToContext(old);
    super.tearDown();
  }

  public void testPlaceholdersInPropertiesAreFolded() throws IOException {
    myFixture.configureByFile("MyClass.java");
    myFixture.configureByFile("i18n.properties");

    String actual = ((CodeInsightTestFixtureImpl)myFixture).getFoldingDescription(true);
    String expected = FileUtil.loadFile(new File(myFixture.getTestDataPath(), "i18n.after"));
    assertEquals(expected, actual);
  }
}