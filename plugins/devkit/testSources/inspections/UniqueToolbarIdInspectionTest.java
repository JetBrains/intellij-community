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

/**
 * @author Konstantin Bulenkov
 */
@TestDataPath("$CONTENT_ROOT/testData/inspections/uniqueToolbarId")
public class UniqueToolbarIdInspectionTest extends PluginModuleTestCase {

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/inspections/uniqueToolbarId";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(UniqueToolbarIdInspection.class);
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class ActionToolbar {}");
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class ActionGroup {}");
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class ActionPlaces {public static final String UNKNOWN = \"\";}");
    myFixture.addClass("package com.intellij.openapi.actionSystem; " +
                       "public class ActionManager {" +
                       "public static ActionManager getInstance() {return new ActionManager();}" +
                       "public ActionToolbar createActionToolbar(@NonNls String place, @NotNull ActionGroup group, boolean horizontal) {return new ActionToolbar();}" +
                       "}");
  }

  public void testUsingEmptyToolbarId() {
    myFixture.testHighlighting("UniqueToolbarIdTestDataClass.java");
  }
}
