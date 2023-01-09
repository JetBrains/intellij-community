// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
abstract public class UnspecifiedActionsPlaceInspectionTestBase extends JavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(UnspecifiedActionsPlaceInspection.class);
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class ActionPopupMenu {}");
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class ActionToolbar {}");
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class ActionGroup {}");
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class ActionPlaces {public static final String UNKNOWN = \"unknown\";}");
    myFixture.addClass("package com.intellij.openapi.actionSystem; " +
                       "public class ActionManager {" +
                       "public static ActionManager getInstance() {return new ActionManager();}" +
                       "public ActionPopupMenu createActionPopupMenu(@NonNls String place, @NotNull ActionGroup group) {return new ActionPopupMenu();}" +
                       "public ActionToolbar createActionToolbar(@NonNls String place, @NotNull ActionGroup group, boolean horizontal) {return new ActionToolbar();}" +
                       "}");
  }

  protected void doTest() {
    myFixture.testHighlighting(getTestName(false) + '.' + getFileExtension());
  }

  @NotNull
  protected abstract String getFileExtension();
}
