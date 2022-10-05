// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

/**
 * @author Konstantin Bulenkov
 */
@TestDataPath("/inspections/unspecifiedActionPlace")
public class UnspecifiedActionsPlaceInspectionTest extends PluginModuleTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/unspecifiedActionsPlace";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(UnspecifiedActionsPlaceInspection.class);
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class ActionPopupMenu {}");
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class ActionToolbar {}");
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class ActionGroup {}");
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class ActionPlaces {public static final String UNKNOWN = \"\";}");
    myFixture.addClass("package com.intellij.openapi.actionSystem; " +
                       "public class ActionManager {" +
                       "public static ActionManager getInstance() {return new ActionManager();}" +
                       "public ActionPopupMenu createActionPopupMenu(@NonNls String place, @NotNull ActionGroup group) {return new ActionPopupMenu();}" +
                       "public ActionToolbar createActionToolbar(@NonNls String place, @NotNull ActionGroup group, boolean horizontal) {return new ActionToolbar();}" +
                       "}");
  }

  public void testUnspecifiedActionsPlaces() {
    myFixture.testHighlighting("UnspecifiedActionsPlaceTestDataClass.java");
  }
}
