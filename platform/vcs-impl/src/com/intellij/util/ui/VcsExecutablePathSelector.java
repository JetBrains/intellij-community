// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.Objects;

public class VcsExecutablePathSelector {
  private final JPanel myMainPanel;
  private final TextFieldWithBrowseButton myPathSelector;
  private final JBCheckBox myProjectPathCheckbox;
  private final JButton myTestButton;
  private final BorderLayoutPanel myErrorComponent = new BorderLayoutPanel(UIUtil.DEFAULT_HGAP, 0);

  @Nullable private String mySavedPath;
  @Nullable private String myAutoDetectedPath = null;

  public VcsExecutablePathSelector(@NotNull @Nls String vcsName, @Nullable Disposable disposable, @NotNull ExecutableHandler handler) {
    BorderLayoutPanel panel = JBUI.Panels.simplePanel(UIUtil.DEFAULT_HGAP, 0);

    myPathSelector = new TextFieldWithBrowseButton(null, disposable);
    myPathSelector.addBrowseFolderListener(VcsBundle.message("executable.select.title"),
                                           null,
                                           null,
                                           FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
                                           new MyTextComponentAccessor(handler));
    myPathSelector.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        updateTestButtonAvailability();
      }
    });
    panel.addToCenter(myPathSelector);

    myTestButton = new JButton(VcsBundle.message("executable.test"));
    myTestButton.addActionListener(e -> testExecutable(handler));
    panel.addToRight(myTestButton);

    myProjectPathCheckbox = new JBCheckBox(VcsBundle.message("executable.project.override"));
    myProjectPathCheckbox.addActionListener(e -> handleProjectOverrideStateChanged());

    JLabel label = new JBLabel(VcsBundle.message("executable.select.label", vcsName));
    label.setLabelFor(panel);

    myMainPanel = new JPanel(new GridBagLayout());
    GridBag gb = new GridBag().setDefaultAnchor(GridBagConstraints.WEST);
    myMainPanel.add(label, gb.nextLine().next().insets(JBUI.insetsRight(UIUtil.DEFAULT_HGAP)));
    myMainPanel.add(panel, gb.next().fillCellHorizontally().weightx(1.0));
    myMainPanel.add(myProjectPathCheckbox, gb.nextLine().next().next());
    myMainPanel.add(myErrorComponent, gb.nextLine().next().next().insets(JBUI.insets(4, 4, 0, 0)));
  }

  @NotNull
  public BorderLayoutPanel getErrorComponent() {
    return myErrorComponent;
  }

  private void handleProjectOverrideStateChanged() {
    if (myProjectPathCheckbox.isSelected()) {
      mySavedPath = getCurrentPath();
    }
    else if (!Objects.equals(getCurrentPath(), mySavedPath)) {

      switch (Messages.showYesNoCancelDialog(myMainPanel,
                                             VcsBundle.message("executable.project.override.reset.message"),
                                             VcsBundle.message("executable.project.override.reset.title"),
                                             VcsBundle.message("executable.project.override.reset.globalize"),
                                             VcsBundle.message("executable.project.override.reset.revert"),
                                             Messages.getCancelButton(),
                                             null)) {
        case Messages.NO -> myPathSelector.setText(mySavedPath);
        case Messages.CANCEL -> myProjectPathCheckbox.setSelected(true);
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

  /**
   * Pass 'null' if path detection is in progress.
   */
  public void setAutoDetectedPath(@Nullable @NlsSafe String autoDetectedPath) {
    StatusText emptyText = ((JBTextField)myPathSelector.getTextField()).getEmptyText();
    emptyText.setText(autoDetectedPath != null ? VcsBundle.message("settings.auto.detected") + autoDetectedPath :
                      VcsBundle.message("settings.auto.detected.progress"));

    myAutoDetectedPath = autoDetectedPath;
    updateTestButtonAvailability();
  }

  /**
   * @param overridden if projectPath takes precedence over the globalPath
   */
  public void reset(@Nullable @NlsSafe String globalPath, boolean overridden, @Nullable @NlsSafe String projectPath) {
    myProjectPathCheckbox.setSelected(overridden);
    if (overridden) {
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

  @Nullable
  private String getExecutablePath() {
    return ObjectUtils.chooseNotNull(getCurrentPath(), myAutoDetectedPath);
  }

  private void updateTestButtonAvailability() {
    myTestButton.setEnabled(getExecutablePath() != null);
  }

  private void testExecutable(@NotNull ExecutableHandler handler) {
    String executable = getExecutablePath();
    if (executable != null) handler.testExecutable(executable);
  }

  private static class MyTextComponentAccessor implements TextComponentAccessor<JTextField> {
    private final ExecutableHandler myHandler;

    private MyTextComponentAccessor(ExecutableHandler handler) {
      myHandler = handler;
    }

    @Override
    public String getText(JTextField textField) {
      return textField.getText();
    }

    @Override
    public void setText(JTextField textField, @NotNull String text) {
      String patchedText = myHandler.patchExecutable(text);
      textField.setText(patchedText != null ? patchedText : text);
    }
  }

  public interface ExecutableHandler {
    @Nullable
    default String patchExecutable(@NotNull String executable) {
      return null;
    }

    void testExecutable(@NotNull String executable);
  }
}
