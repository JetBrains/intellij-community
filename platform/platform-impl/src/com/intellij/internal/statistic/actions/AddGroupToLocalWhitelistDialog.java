// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class AddGroupToLocalWhitelistDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JTextField myGroupIdTextField;
  private JTextField myEventDataTextField;
  private JBLabel myEventDataTipLabel;
  private JBLabel myEventDataLabel;
  private JBLabel myGroupIdLabel;

  protected AddGroupToLocalWhitelistDialog(@NotNull Project project) {
    super(project);
    myGroupIdLabel.setLabelFor(myGroupIdTextField);
    myEventDataLabel.setLabelFor(myEventDataTextField);
    UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, myEventDataTipLabel);
    setOKButtonText("&Add");
    Disposer.register(project, getDisposable());
    setTitle("Add Test Group to Local Whitelist");
    init();
  }

  @Override
  @NotNull
  protected String getDimensionServiceKey() {
    return AddGroupToLocalWhitelistDialog.class.getCanonicalName();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myGroupIdTextField;
  }

  @Nullable
  public String getGroupId() {
    return StringUtil.nullize(myGroupIdTextField.getText());
  }

  @NotNull
  public Set<String> getEventData() {
    final String text = myEventDataTextField.getText().trim();
    return StringUtil.isEmpty(text) ? Collections.emptySet() :
           StringUtil.split(text, ";").stream().
             map(field -> field.trim()).
             filter(field -> !field.isEmpty()).
             collect(Collectors.toSet());
  }
}
