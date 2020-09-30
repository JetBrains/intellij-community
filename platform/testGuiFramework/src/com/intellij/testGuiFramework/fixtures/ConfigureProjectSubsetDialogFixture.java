// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures;

import com.intellij.testGuiFramework.framework.GuiTestUtil;
import com.intellij.testGuiFramework.framework.Timeouts;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.DialogMatcher;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JTableCellFixture;
import org.fest.swing.fixture.JTableFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static org.fest.swing.core.matcher.DialogMatcher.withTitle;
import static org.fest.swing.data.TableCell.row;
import static org.fest.swing.finder.WindowFinder.findDialog;

public final class ConfigureProjectSubsetDialogFixture {
  @NotNull private final DialogFixture myDialog;
  @NotNull private final JTableFixture myModulesTable;

  @NotNull
  public static ConfigureProjectSubsetDialogFixture find(@NotNull Robot robot) {
    DialogMatcher matcher = withTitle("Select Modules to Include in Project Subset").andShowing();
    DialogFixture dialog = findDialog(matcher).withTimeout(Timeouts.INSTANCE.getMinutes02().duration()).using(robot);
    return new ConfigureProjectSubsetDialogFixture(dialog);
  }

  private ConfigureProjectSubsetDialogFixture(@NotNull DialogFixture dialog) {
    myDialog = dialog;
    Robot robot = dialog.robot();
    myModulesTable = new JTableFixture(robot, robot.finder().findByType(dialog.target(), JTable.class, true));
  }

  @NotNull
  public ConfigureProjectSubsetDialogFixture selectModule(@NotNull String moduleName, boolean selected) {
    JTableCellFixture cell = myModulesTable.cell(moduleName);
    myModulesTable.enterValue(row(cell.row()).column(0), String.valueOf(selected));
    return this;
  }

  public void clickOk() {
    GuiTestUtil.INSTANCE.findAndClickOkButton(myDialog);
  }
}
