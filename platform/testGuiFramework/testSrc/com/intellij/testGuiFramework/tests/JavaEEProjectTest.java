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
package com.intellij.testGuiFramework.tests;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.testGuiFramework.fixtures.ProjectViewFixture;
import com.intellij.testGuiFramework.fixtures.ToolWindowFixture;
import com.intellij.testGuiFramework.fixtures.WelcomeFrameFixture;
import com.intellij.testGuiFramework.fixtures.newProjectWizard.NewProjectWizardFixture;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import com.intellij.testGuiFramework.impl.GuiTestCase;
import org.junit.Test;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.intellij.testGuiFramework.framework.GuiTestUtil.getSystemJdk;

/**
 * @author Sergey Karashevich
 */
public class JavaEEProjectTest extends GuiTestCase {

  @Test
  public void testJavaEEProject() throws IOException, InterruptedException {

    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm");
    Date date = new Date();
    String projectName = "smoke-test-project-" + dateFormat.format(date);

    WelcomeFrameFixture welcomeFrame = findWelcomeFrame();
    welcomeFrame.createNewProject();
    NewProjectWizardFixture newProjectWizard = findNewProjectWizard();

    setupJdk(newProjectWizard);

    //select project type and framework
    newProjectWizard.
      selectProjectType("Java").
      selectFramework("JavaEE Persistence");
    newProjectWizard.clickNext();
    newProjectWizard.setProjectName(projectName);
    final File locationInFileSystem = newProjectWizard.getLocationInFileSystem();
    newProjectWizard.clickFinish();

    myProjectFrame = findIdeFrame(projectName, locationInFileSystem);
    myProjectFrame.waitForBackgroundTasksToFinish();

    final ProjectViewFixture projectView = myProjectFrame.getProjectView();
    final ProjectViewFixture.PaneFixture paneFixture = projectView.selectProjectPane();

    paneFixture.selectByPath(projectName, "src", "META-INF", "persistence.xml");
    ToolWindowFixture.showToolwindowStripes(myRobot);

    //prevent from ProjectLeak (if the project is closed during the indexing
    DumbService.getInstance(myProjectFrame.getProject()).waitForSmartMode();

    //final JToggleButtonFixture persistence = (new JToggleButtonFinder("Persistence")).withTimeout(THIRTY_SEC_TIMEOUT.duration()).using(myRobot);
    //persistence.click();

    //ToolWindowFixture.clickToolwindowButton("Persistence", myRobot);
    //ToolWindowFixture.clickToolwindowButton("Java Enterprise", myRobot);
  }

  private void setupJdk(NewProjectWizardFixture newProjectWizard) {
    if (newProjectWizard.isJdkEmpty()) {
      JButton newButton = GuiTestUtil.findButton(newProjectWizard,
                                                 GuiTestUtil.adduction(ApplicationBundle.message("button.new")),
                                                 myRobot);
      myRobot.click(newButton);
      File javaSdkPath = new File(getSystemJdk());
      String sdkType = GuiTestUtil.adduction(ProjectBundle.message("sdk.java.name"));
      GuiTestUtil.clickPopupMenuItem(sdkType, newButton, myRobot);
      newProjectWizard.selectSdkPath(javaSdkPath, sdkType);
    }
  }

  private void setupJdk(NewProjectWizardFixture newProjectWizard, String customPath){
    if (newProjectWizard.isJdkEmpty()) {
      JButton newButton = GuiTestUtil.findButton(newProjectWizard,
                                                 GuiTestUtil.adduction(ApplicationBundle.message("button.new")),
                                                 myRobot);
      myRobot.click(newButton);
      File javaSdkPath = new File(customPath);
      String sdkType = GuiTestUtil.adduction(ProjectBundle.message("sdk.java.name"));
      GuiTestUtil.clickPopupMenuItem(sdkType, newButton, myRobot);
      newProjectWizard.selectSdkPath(javaSdkPath, sdkType);
    }
  }

}
