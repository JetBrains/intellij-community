// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.maddyhome.idea.copyright.ui;

import com.intellij.copyright.CopyrightBundle;
import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.maddyhome.idea.copyright.CopyrightUpdaters;
import com.maddyhome.idea.copyright.util.FileTypeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class CopyrightFormattingConfigurable extends SearchableConfigurable.Parent.Abstract implements Configurable.NoScroll, Configurable.WithEpDependencies {
  private final Project myProject;
  private TemplateCommentPanel myPanel;

  CopyrightFormattingConfigurable(Project project) {
    myProject = project;
  }

  @Override
  @NotNull
  public String getId() {
    return "template.copyright.formatting";
  }

  @Override
  @Nls
  public String getDisplayName() {
    return CopyrightBundle.message("configurable.CopyrightFormattingConfigurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return getId();
  }

  @Override
  public JComponent createComponent() {
    getOrCreateMainPanel();
    return myPanel.createComponent();
  }

  private TemplateCommentPanel getOrCreateMainPanel() {
    if (myPanel == null) {
      myPanel = new TemplateCommentPanel(null, null, myProject);
    }
    return myPanel;
  }

  @Override
  public boolean isModified() {
    return myPanel.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  @Override
  public void reset() {
    myPanel.reset();
  }

  @Override
  public void disposeUIResources() {
    myPanel.disposeUIResources();
    myPanel = null;
  }

  @Override
  public boolean hasOwnContent() {
    return true;
  }

  @Override
  protected Configurable[] buildConfigurables() {
    final FileType[] types = FileTypeUtil.getSupportedTypes().toArray(FileType.EMPTY_ARRAY);
    final Configurable[] children = new Configurable[types.length];
    Arrays.sort(types, new FileTypeUtil.SortByName());
    for (int i = 0; i < types.length; i++) {
      children[i] = FileTypeCopyrightConfigurableFactory.createFileTypeConfigurable(myProject, types[i], getOrCreateMainPanel());
    }
    return children;
  }

  @NotNull
  @Override
  public Collection<BaseExtensionPointName<?>> getDependencies() {
    return Collections.singletonList(CopyrightUpdaters.EP_NAME);
  }
}
