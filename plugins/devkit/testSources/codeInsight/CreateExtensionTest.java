/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.codeInsight;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

/**
 * @author Dmitry Avdeev
 *         Date: 1/20/12
 */
public class CreateExtensionTest extends JavaCodeInsightFixtureTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package com.intellij.codeInspection; public class LocalInspectionTool {} ");
    myFixture.addClass("package com.intellij.codeInspection; public class GlobalInspectionTool {} ");
    myFixture.enableInspections(new UnusedDeclarationInspection(), new UnusedSymbolLocalInspection());
  }

  public void testCreateLocalInspectionMapping() throws Exception {
    myFixture.testHighlighting("LocalInspection.java", "plugin.xml");
    IntentionAction intention = myFixture.findSingleIntention("Register inspection");
    myFixture.launchAction(intention);
    myFixture.checkResultByFile("plugin.xml", "localInspection.xml", true);
  }

  public void testCreateGlobalInspectionMapping() throws Exception {
    myFixture.testHighlighting("GlobalInspection.java", "localInspection.xml");
    IntentionAction intention = myFixture.findSingleIntention("Register inspection");
    myFixture.launchAction(intention);
    myFixture.checkResultByFile("localInspection.xml", "globalInspection.xml", true);
  }

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/extensions";
  }

}
