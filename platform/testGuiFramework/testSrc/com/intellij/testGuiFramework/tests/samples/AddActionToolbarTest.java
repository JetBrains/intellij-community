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
package com.intellij.testGuiFramework.tests.samples;

import com.intellij.ide.ui.UISettings;
import com.intellij.testGuiFramework.fixtures.ActionButtonFixture;
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture;
import com.intellij.testGuiFramework.fixtures.JDialogFixture;
import com.intellij.testGuiFramework.fixtures.SettingsTreeFixture;
import com.intellij.testGuiFramework.impl.GuiTestCase;
import com.intellij.ui.treeStructure.Tree;
import org.fest.swing.timing.Pause;
import org.junit.Ignore;
import org.junit.Test;

import static com.intellij.testGuiFramework.framework.GuiTestUtil.*;

public class AddActionToolbarTest extends GuiTestCase {

  @Test @Ignore
  //Mac only test
  public void testAddActionToolbar() throws Exception {

    //import project
    IdeFrameFixture ideFrameFixture = importSimpleProject();
    ideFrameFixture.waitForBackgroundTasksToFinish();

    //check toolbar and open if is hidden
    if (!UISettings.getInstance().getShowMainToolbar()) {
      ideFrameFixture.invokeMenuPath("View", "Toolbar");
    }

    //open Settings
    invokeActionViaShortcut(myRobot, "meta comma");

    //find settings dialog
    JDialogFixture preferencesDialog = JDialogFixture.find(myRobot, "Preferences");

    //SettingsTree Appearance & Behavior -> Menus and Toolbars
    SettingsTreeFixture.find(myRobot).select("Appearance & Behavior/Menus and Toolbars");
    Pause.pause(2000L);

    //tree: Main Toolbar/Help
    findJTreeFixtureByClassName(myRobot, preferencesDialog.target(), Tree.class.getName()).clickPath("Main Toolbar/Help");
    //click Add After...
    findAndClickButton(preferencesDialog, "Add After...");
    //Choose Actions To Add
    JDialogFixture dialogFixture = JDialogFixture.find(myRobot, "Choose Actions To Add");
    //tree: All Actions/Main menu/File/Print...
    findJTreeFixtureByClassName(myRobot, dialogFixture.target(), Tree.class.getName()).clickPath("All Actions/Main menu/File/Print...");
    //clickOK
    findAndClickOkButton(dialogFixture);
    //clickOk
    findAndClickOkButton(preferencesDialog);
    //choose File in project tree

    ideFrameFixture.getProjectView().selectProjectPane().expandByPath(ideFrameFixture.getProject().getName(), "src", "Main.java").click();
    //ActionButton("Print") wait and click
    ActionButtonFixture.findByActionId("Print", myRobot, ideFrameFixture.target()).waitUntilEnabledAndShowing().click();
    //Dialog("Print")
    JDialogFixture printDialog = JDialogFixture.find(myRobot, "Print");
    //close dialog
    findAndClickCancelButton(printDialog);
    Pause.pause(5000L);
  }
}
