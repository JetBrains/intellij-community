// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.application.options.pathMacros.PathMacroConfigurable;
import com.intellij.application.options.pathMacros.PathMacroListEditor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 */
public class UndefinedMacrosConfigurable implements Configurable{
  private PathMacroListEditor myEditor;
  private final @NlsContexts.Label String myText;
  private final Collection<String> myUndefinedMacroNames;

  public UndefinedMacrosConfigurable(@NlsContexts.Label String text, Collection<String> undefinedMacroNames) {
    myText = text;
    myUndefinedMacroNames = undefinedMacroNames;
  }

  @Override
  public String getHelpTopic() {
    return PathMacroConfigurable.HELP_ID;
  }

  @Override
  public String getDisplayName() {
    return ProjectBundle.message("project.configure.path.variables.title");
  }

  @Override
  public JComponent createComponent() {
    final JPanel mainPanel = new JPanel(new BorderLayout());
    // important: do not allow to remove or change macro name for already defined macros befor project is loaded
    myEditor = new PathMacroListEditor(myUndefinedMacroNames);
    final JComponent editorPanel = myEditor.getPanel();

    mainPanel.add(editorPanel, BorderLayout.CENTER);

    final JLabel textLabel = new JLabel(myText);
    textLabel.setUI(new MultiLineLabelUI());
    textLabel.setBorder(JBUI.Borders.empty(6, 6, 6, 6));
    mainPanel.add(textLabel, BorderLayout.NORTH);

    return mainPanel;
  }

  @Override
  public boolean isModified() {
    return myEditor.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    myEditor.commit();
  }

  @Override
  public void reset() {
    myEditor.reset();
  }

  @Override
  public void disposeUIResources() {
    myEditor = null;
  }
}
