// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.panel.JBPanelFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;
import java.util.function.Consumer;

public class VcsExecutablePathSelector {
  private final JPanel myMainPanel;
  private final TextFieldWithBrowseButton myPathSelector;
  private final JButton myTestButton;
  private final JBCheckBox myProjectPathCheckbox;

  @Nullable private String mySavedPath;
  private String myAutoDetectedPath;

  public VcsExecutablePathSelector(@NotNull Consumer<String> executableTester) {
    BorderLayoutPanel panel = JBUI.Panels.simplePanel(UIUtil.DEFAULT_HGAP, 0);

    myPathSelector = new TextFieldWithBrowseButton(new JBTextField(10));
    myPathSelector.addBrowseFolderListener(VcsBundle.getString("executable.select.title"),
                                           null,
                                           null,
                                           FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    panel.addToCenter(myPathSelector);

    myTestButton = new JButton(VcsBundle.getString("executable.test"));
    myTestButton.addActionListener(e -> executableTester.accept(ObjectUtils.notNull(getCurrentPath(), myAutoDetectedPath)));
    panel.addToRight(myTestButton);

    myProjectPathCheckbox = new JBCheckBox(VcsBundle.getString("executable.project.override"));
    myProjectPathCheckbox.addActionListener(e -> handleProjectOverrideStateChanged());

    myMainPanel = JBPanelFactory.grid()
      .add(JBPanelFactory.panel(panel).withLabel(VcsBundle.getString("executable.select.label")))
      .add(JBPanelFactory.panel(myProjectPathCheckbox))
      .createPanel();
  }

  private void handleProjectOverrideStateChanged() {
    if (myProjectPathCheckbox.isSelected()) {
      mySavedPath = getCurrentPath();
    }
    else if (!Objects.equals(getCurrentPath(), mySavedPath)) {

      switch (Messages.showYesNoCancelDialog(myMainPanel,
                                             VcsBundle.getString("executable.project.override.reset.message"),
                                             VcsBundle.getString("executable.project.override.reset.title"),
                                             VcsBundle.getString("executable.project.override.reset.globalize"),
                                             VcsBundle.getString("executable.project.override.reset.revert"),
                                             Messages.CANCEL_BUTTON,
                                             null)) {
        case Messages.NO:
          myPathSelector.setText(mySavedPath);
          break;
        case Messages.CANCEL:
          myProjectPathCheckbox.setSelected(true);
          break;
      }
    }
  }

  @Nullable
  public String getCurrentPath() {
    return StringUtil.nullize(myPathSelector.getText().trim());
  }

  public boolean isOverridden() {
    return myProjectPathCheckbox.isSelected();
  }

  public void reset(@Nullable String globalPath,
                    boolean pathOverriddenForProject,
                    @Nullable String projectPath,
                    @NotNull String autoDetectedPath) {
    myAutoDetectedPath = autoDetectedPath;
    ((JBTextField)myPathSelector.getTextField()).getEmptyText().setText("Auto-detected: " + myAutoDetectedPath);

    myProjectPathCheckbox.setSelected(pathOverriddenForProject);
    if (pathOverriddenForProject) {
      mySavedPath = globalPath;
      myPathSelector.setText(projectPath);
    }
    else {
      myPathSelector.setText(globalPath);
    }
  }

  public boolean isModified(@Nullable String globalPath, boolean overridden, @Nullable String projectPath) {
    if (myProjectPathCheckbox.isSelected() != overridden) return true;

    if (myProjectPathCheckbox.isSelected()) {
      return !Objects.equals(getCurrentPath(), projectPath);
    }
    else {
      return !Objects.equals(getCurrentPath(), globalPath);
    }
  }

  @NotNull
  public JPanel getMainPanel() {
    return myMainPanel;
  }
}
