/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.intelliLang;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Provides UI for global {@link org.intellij.plugins.intelliLang.Configuration}. This is
 * split into this and the SettingsUI class to avoid holding strong application-wide
 * references on the UI objects.
 * <p/>
 * Even though this class is an application component, it determines the currently focused
 * Project and passes it to the SettingsUI instance. This is kinda unconventional, but has
 * the benefit of having a centralized IDE-wide configuration that doesn't need to be
 * recreated and maintained for each project, and still being able to use class-choosers
 * that will allow to browse the current project's classes.
 */
public class Settings implements SearchableConfigurable {

  private final Configuration myConfiguration;

  Settings(Configuration configuration) {
    myConfiguration = configuration;
  }

  private SettingsUI mySettingsUI;

  public String getDisplayName() {
    return "Language Injection";
  }

  @Nullable
  public Icon getIcon() {
    return IconLoader.getIcon("icon.png");
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    final Configurable child = mySettingsUI == null? null : mySettingsUI.getSelectedChild();
    final String topic = child != null ? child.getHelpTopic() : null;
    return topic != null? topic : "IntelliLang.Configuration";
  }

  public JComponent createComponent() {
    final ProjectManager projectManager = ProjectManager.getInstance();
    final Project[] projects = projectManager.getOpenProjects();

    Project project = null;
    if (projects.length == 0) {
      project = projectManager.getDefaultProject();
    }
    else {
      final WindowManagerEx windowManager = WindowManagerEx.getInstanceEx();
      final Window focusedWindow = windowManager.getMostRecentFocusedWindow();
      if (focusedWindow != null) {
        for (Project p : projects) {
          final Window w = windowManager.suggestParentWindow(p);
          if (w == focusedWindow || w.isAncestorOf(focusedWindow) || focusedWindow.isAncestorOf(w)) {
            project = p;
            break;
          }
        }
      }
      if (project == null) {
        project = projectManager.getDefaultProject();
      }
    }

    mySettingsUI = new SettingsUI(project, myConfiguration);

    return mySettingsUI.createComponent();
  }

  public boolean isModified() {
    return mySettingsUI != null && mySettingsUI.isModified();
  }

  public void apply() throws ConfigurationException {
    mySettingsUI.apply();
  }

  public void reset() {
    mySettingsUI.reset();
  }

  public void disposeUIResources() {
    mySettingsUI = null;
  }

  public String getId() {
    return "IntelliLang.Configuration";
  }

  public Runnable enableSearch(String option) {
    return null;
  }
}
