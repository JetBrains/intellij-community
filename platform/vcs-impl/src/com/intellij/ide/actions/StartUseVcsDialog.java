// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.util.SystemInfo.isMac;

class StartUseVcsDialog extends DialogWrapper {
  private final Map<String, String> myVcses;
  private VcsCombo myVcsCombo;
  private String mySelected;

  StartUseVcsDialog(@NotNull Project project) {
    super(project, true);
    myVcses = getVcses(project);
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

    String path = isMac? VcsBundle.message("vcs.settings.path.mac") : VcsBundle.message("vcs.settings.path");
    JLabel helpText = new JLabel(VcsBundle.message("dialog.enable.version.control.integration.hint.text") + path);
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

  @Override
  protected String getHelpId() {
    return "reference.version.control.enable.version.control.integration";
  }

  @Override
  protected void doOKAction() {
    mySelected = myVcsCombo.getSelectedItem();
    super.doOKAction();
  }

  private String @NotNull [] prepareComboData() {
    ArrayList<String> keys = new ArrayList<>(myVcses.keySet());
    keys.sort((String o1, String o2) -> {
      if (o1.equals(o2)) return 0;
      if (o1.equals("Git")) return -1;
      if (o2.equals("Git")) return 1;
      return o1.compareTo(o2);
    });
    return ArrayUtil.toStringArray(keys);
  }

  @NotNull
  String getVcs() {
    return myVcses.get(mySelected);
  }

  private static Map<String, String> getVcses(@NotNull Project project) {
    VcsDescriptor[] allVcss = ProjectLevelVcsManager.getInstance(project).getAllVcss();
    Map<String, String> map = new HashMap<>(allVcss.length);
    for (VcsDescriptor vcs : allVcss) {
      map.put(vcs.getDisplayName(), vcs.getName());
    }
    return map;
  }

  private static final class VcsCombo extends JComboBox<String> {
    private VcsCombo(String @NotNull [] items) {
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
