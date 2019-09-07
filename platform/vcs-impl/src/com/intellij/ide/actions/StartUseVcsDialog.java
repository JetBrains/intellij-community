// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.util.ObjectUtils.notNull;

class StartUseVcsDialog extends DialogWrapper {
  private final VcsDataWrapper myData;
  private VcsCombo myVcsCombo;
  private String mySelected;

  StartUseVcsDialog(@NotNull VcsDataWrapper data) {
    super(data.getProject(), true);
    myData = data;
    setTitle(VcsBundle.message("dialog.enable.version.control.integration.title"));

    init();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myVcsCombo;
  }

  @Override
  protected JComponent createCenterPanel() {
    JLabel selectText = new JLabel(VcsBundle.message("dialog.enable.version.control.integration.select.vcs.label.text"));
    selectText.setUI(new MultiLineLabelUI());

    JPanel mainPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.BASELINE, GridBagConstraints.NONE, JBUI.insets(5),
                                                   0, 0);
    mainPanel.add(selectText, gb);

    ++gb.gridx;

    myVcsCombo = new VcsCombo(prepareComboData());
    mainPanel.add(myVcsCombo, gb);

    myVcsCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        validateVcs();
      }
    });
    validateVcs();

    JLabel helpText = new JLabel(VcsBundle.message("dialog.enable.version.control.integration.hint.text"));
    helpText.setUI(new MultiLineLabelUI());
    helpText.setForeground(UIUtil.getInactiveTextColor());

    gb.anchor = GridBagConstraints.NORTHWEST;
    gb.gridx = 0;
    ++ gb.gridy;
    gb.gridwidth = 2;
    mainPanel.add(helpText, gb);

    JPanel wrapper = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                    JBUI.emptyInsets(), 0, 0);
    wrapper.add(mainPanel, gbc);
    return wrapper;
  }

  private void validateVcs() {
    String selectedVcs = notNull(myVcsCombo.getSelectedItem());
    setOKActionEnabled(selectedVcs.length() > 0);
  }

  @Override
  protected String getHelpId() {
    return "reference.version.control.enable.version.control.integration";
  }

  @Override
  protected void doOKAction() {
    mySelected = myVcsCombo.getSelectedItem();
    super.doOKAction();
  }

  @NotNull
  private String[] prepareComboData() {
    Collection<String> displayNames = myData.getVcses().keySet();
    List<String> keys = new ArrayList<>(displayNames.size() + 1);
    keys.add("");
    keys.addAll(displayNames);
    Collections.sort(keys);
    return ArrayUtil.toStringArray(keys);
  }

  @NotNull
  String getVcs() {
    return myData.getVcses().get(mySelected);
  }

  private static class VcsCombo extends JComboBox<String> {
    private VcsCombo(@NotNull String[] items) {
      super(items);
      setSelectedIndex(0);
      setEditable(false);
    }

    @Override
    public String getSelectedItem() {
      return (String) super.getSelectedItem();
    }
  }
}
