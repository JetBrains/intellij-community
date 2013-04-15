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

/*
 * User: anna
 * Date: 04-Dec-2008
 */
package com.maddyhome.idea.copyright.ui;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.maddyhome.idea.copyright.util.FileTypeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;

public class CopyrightFormattingConfigurable extends SearchableConfigurable.Parent.Abstract implements Configurable.NoScroll {
  private final Project myProject;
  private TemplateCommentPanel myPanel;

  CopyrightFormattingConfigurable(Project project) {
    myProject = project;
  }

  @NotNull
  public String getId() {
    return "template.copyright.formatting";
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
    public String getDisplayName() {
    return "Formatting";
  }

  public String getHelpTopic() {
    return getId();
  }

  public JComponent createComponent() {
    getOrCreateMainPanel();
    return myPanel.createComponent();
  }

  private TemplateCommentPanel getOrCreateMainPanel() {
    if (myPanel == null) {
      myPanel = new TemplateCommentPanel(null, null, null, myProject);
    }
    return myPanel;
  }

  public boolean isModified() {
    return myPanel.isModified();
  }

  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  public void reset() {
    myPanel.reset();
  }

  public void disposeUIResources() {
    myPanel.disposeUIResources();
    myPanel = null;
  }

  public boolean hasOwnContent() {
    return true;
  }

  protected Configurable[] buildConfigurables() {
    final FileType[] types = FileTypeUtil.getInstance().getSupportedTypes();
    final Configurable[] children = new Configurable[types.length];
    Arrays.sort(types, new FileTypeUtil.SortByName());
    for (int i = 0; i < types.length; i++) {
      children[i] = FileTypeCopyrightConfigurableFactory.createFileTypeConfigurable(myProject, types[i], getOrCreateMainPanel());
    }
    return children;
  }
}
