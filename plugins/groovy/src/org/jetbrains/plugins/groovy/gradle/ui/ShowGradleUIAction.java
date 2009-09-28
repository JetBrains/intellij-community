/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jetbrains.plugins.groovy.gradle.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import org.gradle.openapi.external.ui.SettingsNodeVersion1;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.gradle.GradleLibraryManager;

import javax.swing.*;
import java.awt.*;

//this shows the gradle UI if its not already visible
public class ShowGradleUIAction extends AnAction implements DumbAware {

  public void actionPerformed(AnActionEvent e) {
    new GradleUiDialog(e.getData(DataKeys.PROJECT), e.getData(DataKeys.MODULE)).show();
  }

  @Override
  public void update(AnActionEvent e) {
    boolean enabled = isEnabled(e);
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled);
  }

  private static boolean isEnabled(AnActionEvent e) {
    if (!"true".equals(System.getProperty("gradle.ui.action"))) {
      return false;
    }

    Project project = e.getData(DataKeys.PROJECT);
    return project != null && GradleLibraryManager.getSdkHome(e.getData(DataKeys.MODULE), project) != null;
  }

  private static class GradleUiDialog extends DialogWrapper {
    private static final String MAIN_COMPONENT_AS_DIALOG = "main-component-as-dialog";
    private static final String LOCATION_Y = "location-y";
    private static final String LOCATION_X = "location-x";
    private static final String HEIGHT = "height";
    private static final String WIDTH = "width";

    private final GradlePanelWrapper myGradlePanelWrapper;
    private Boolean canClose;
    private final Project myProject;

    public GradleUiDialog(final Project project, @Nullable final Module module) {
      super(project);
      myProject = project;
      myGradlePanelWrapper = new GradlePanelWrapper();
      myGradlePanelWrapper.initalize(module, project);
      Disposer.register(getDisposable(), new Disposable() {
        public void dispose() {
          saveSettings(project);
          myGradlePanelWrapper.close();
        }
      });
      setModal(false);
      setSize(400, 700);
      setTitle("Gradle");

      ProjectManager.getInstance().addProjectManagerListener(project, new ProjectManagerAdapter() {
        public boolean canCloseProject(Project project) {
          if (canClose == null) {
            canClose = myGradlePanelWrapper.canClose();
          }
          return canClose;
        }
      });

      init();
    }

    @Override
    public void show() {
      super.show();
      restoreSettings(myProject);
    }

    @Override
    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(myGradlePanelWrapper.getComponent());
      panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
      return panel;
    }


    //this restores the location and size of the dialog
    private void restoreSettings(Project project) {
      GradleUISettings gradleUISettings = GradleUISettings.getInstance(project);
      SettingsNodeVersion1 settingsNode = gradleUISettings.getRootNode().getChildNode(MAIN_COMPONENT_AS_DIALOG);
      if (settingsNode != null) {
        int x = settingsNode.getValueOfChildAsInt(LOCATION_X, getLocation().x);
        int y = settingsNode.getValueOfChildAsInt(LOCATION_Y, getLocation().y);
        int width = settingsNode.getValueOfChildAsInt(WIDTH, getSize().width);
        int height = settingsNode.getValueOfChildAsInt(HEIGHT, getSize().height);

        setLocation(x, y);
        setSize(width, height);
      }
    }

    //this restores the location and size of the dialog
    private void saveSettings(Project project) {
      GradleUISettings gradleUISettings = GradleUISettings.getInstance(project);
      SettingsNodeVersion1 settingsNode = gradleUISettings.getRootNode().addChildIfNotPresent(MAIN_COMPONENT_AS_DIALOG);

      settingsNode.setValueOfChildAsInt(LOCATION_X, getLocation().x);
      settingsNode.setValueOfChildAsInt(LOCATION_Y, getLocation().y);
      settingsNode.setValueOfChildAsInt(WIDTH, getSize().width);
      settingsNode.setValueOfChildAsInt(HEIGHT, getSize().height);
    }

  }
}
