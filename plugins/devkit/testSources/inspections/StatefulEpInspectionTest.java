/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.TestDataPath;

@TestDataPath("$CONTENT_ROOT/testData/inspections/statefulEp")
public class StatefulEpInspectionTest extends PluginModuleTestCase {
  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/inspections/statefulEp";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package com.intellij.openapi.project; public class Project {}");
    myFixture.addClass("package com.intellij.psi; public class PsiElement {}");
    myFixture.addClass("package com.intellij.psi; public class PsiReference {}");
    myFixture.addClass("package com.intellij.codeInspection; public class LocalQuickFix {}");
    myFixture.addClass("package com.intellij.openapi.components; public interface ProjectComponent {}");
    myFixture.enableInspections(new StatefulEpInspection());
  }

  public void testLocalQuickFix() {
    myFixture.testHighlighting("Fix.java");
  }

  public void testNonFix() {
    setPluginXml("plugin.xml");
    myFixture.testHighlighting("NonFix.java");
  }

  public void testExt() {
    setPluginXml("plugin.xml");
    myFixture.testHighlighting("Ext.java");
  }
  
  public void testProjectComp() {
    setPluginXml("plugin.xml");
    myFixture.testHighlighting("ProjectComp.java");
  }
  
  public void testProjectService() {
    setPluginXml("plugin.xml");
    myFixture.testHighlighting("ProjectService.java");
  }
}
