/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.fixtures;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import com.intellij.ui.treeStructure.Tree;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.timing.Condition;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;

import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.swing.timing.Pause.pause;

public class SelectSdkDialogFixture implements ContainerFixture<JDialog>{

  private final JDialog myDialog;
  private final Robot myRobot;

  public SelectSdkDialogFixture(@NotNull Robot robot, JDialog selectSdkDialog) {
    myRobot = robot;
    myDialog = selectSdkDialog;
  }

  @NotNull
  public static SelectSdkDialogFixture find(@NotNull Robot robot, String sdkType) {
    JDialog dialog = robot.finder().find(new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        return ProjectBundle.message("sdk.configure.home.title", sdkType).equals(dialog.getTitle()) && dialog.isShowing();
      }
    });
    return new SelectSdkDialogFixture(robot, dialog);
  }

  public SelectSdkDialogFixture selectPathToSdk(@NotNull File pathToSdk) {
    final JTextField textField = myRobot.finder().findByType(JTextField.class);
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        textField.setText(pathToSdk.getPath());
      }
    });


    final Tree tree = myRobot.finder().findByType(myDialog, Tree.class);
    final AbstractTreeBuilder builder = AbstractTreeBuilder.getBuilderFor(tree);
    pause(new Condition("Wait until path is updated") {
      @Override
      public boolean test() {
        //noinspection ConstantConditions
        return execute(new GuiQuery<Boolean>() {
          @Override
          protected Boolean executeInEDT() throws Throwable {
            return (textField.getText().equals(pathToSdk.getPath()) && !builder.getUi().getUpdater().hasNodesToUpdate()) ;
          }
        });
      }
    }, GuiTestUtil.INSTANCE.getSHORT_TIMEOUT());
    return this;
  }

  public void clickOk(){
    pause(new Condition("Waiting when ok button at SDK select dialog will be ready for a click") {
      @Override
      public boolean test() {
        JButton button = GuiTestUtil.INSTANCE.findButton(SelectSdkDialogFixture.this, "OK", myRobot);
        return button.isEnabled();
      }
    }, GuiTestUtil.INSTANCE.getSHORT_TIMEOUT());

    GuiTestUtil.INSTANCE.findAndClickOkButton(this);
  }

  @Override
  public JDialog target() {
    return myDialog;
  }

  @Override
  public Robot robot() {
    return myRobot;
  }
}
