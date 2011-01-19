/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.options.ex;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

/**
 * @author max
 */
public class ProjectConfigurableWrapper implements SearchableConfigurable {
  private final Project myProject;
  private final Configurable myDelegate;

  public ProjectConfigurableWrapper(Project project, Configurable delegate) {
    myProject = project;
    myDelegate = delegate;
  }

  public String getDisplayName() {
    return myDelegate.getDisplayName();
  }

  public void reset() {
    myDelegate.reset();
  }

  public void apply() throws ConfigurationException {
    checkProjectFileWritable();
    myDelegate.apply();
  }

  private void checkProjectFileWritable() {
    String path = myProject.getPresentableUrl();
    if (path != null) {
      File file = new File(path);
      if (file.exists() && !file.canWrite()) {
        Messages.showMessageDialog(
          myProject,
          OptionsBundle.message("project.file.read.only.error.message"),
          OptionsBundle.message("cannot.save.settings.default.dialog.title"),
          Messages.getErrorIcon()
        );
      }
    }
  }

  public String getHelpTopic() {
    return myDelegate.getHelpTopic();
  }

  public void disposeUIResources() {
    myDelegate.disposeUIResources();
  }

  public boolean isModified() {
    return myDelegate.isModified();
  }

  public JComponent createComponent() {
    return myDelegate.createComponent();
  }

  public Icon getIcon() {
    return myDelegate.getIcon();
  }

  @NotNull
  @NonNls
  public String getId() {
    return myDelegate instanceof SearchableConfigurable ? ((SearchableConfigurable)myDelegate).getId() : "";
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return myDelegate instanceof SearchableConfigurable ? ((SearchableConfigurable)myDelegate).enableSearch(option) : null;  
  }
}
