// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;

import static com.intellij.openapi.util.SystemInfo.isMac;

public class StartUseVcsDialog extends DialogWrapper {
  @NonNls private static final String GIT = "Git";

  private final ComboBox<AbstractVcs> myVcsCombo;

  @NotNull
  private final String myTargetDirectory;

  private static final Comparator<AbstractVcs> VCS_COMPARATOR = Comparator
    .comparingInt((AbstractVcs vcs) -> GIT.equals(vcs.getName()) ? -1 : 0)
    .thenComparing(vcs -> vcs.getDisplayName(), String.CASE_INSENSITIVE_ORDER);

  public StartUseVcsDialog(@NotNull Project project, @NotNull String targetDirectory) {
    super(project, true);

    myTargetDirectory = targetDirectory;
    AbstractVcs[] vcses = ProjectLevelVcsManager.getInstance(project).getAllSupportedVcss();
    ContainerUtil.sort(vcses, VCS_COMPARATOR);
    myVcsCombo = new ComboBox<>(vcses);
    myVcsCombo.setRenderer(SimpleListCellRenderer.create("", AbstractVcs::getDisplayName));

    setTitle(VcsBundle.message("dialog.enable.version.control.integration.title"));

    init();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myVcsCombo;
  }

  @Override
  protected JComponent createCenterPanel() {
    JLabel selectText = new JLabel(
      VcsBundle.message("dialog.enable.version.control.integration.select.vcs.label.text", PathUtil.getFileName(myTargetDirectory)));
    selectText.setUI(new MultiLineLabelUI());

    JPanel mainPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.BASELINE, GridBagConstraints.NONE, JBUI.insets(5),
                                                   0, 0);
    mainPanel.add(selectText, gb);

    ++gb.gridx;

    mainPanel.add(myVcsCombo, gb);

    String path = isMac ? VcsBundle.message("vcs.settings.path.mac") : VcsBundle.message("vcs.settings.path");
    JLabel helpText = new JLabel(VcsBundle.message("dialog.enable.version.control.integration.hint.text") + path);
    helpText.setUI(new MultiLineLabelUI());
    helpText.setForeground(UIUtil.getInactiveTextColor());

    gb.anchor = GridBagConstraints.NORTHWEST;
    gb.gridx = 0;
    ++gb.gridy;
    gb.gridwidth = 2;
    mainPanel.add(helpText, gb);

    JPanel wrapper = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                    JBInsets.emptyInsets(), 0, 0);
    wrapper.add(mainPanel, gbc);
    return wrapper;
  }

  @Override
  protected String getHelpId() {
    return "reference.version.control.enable.version.control.integration";
  }

  @NotNull
  public AbstractVcs getVcs() {
    return myVcsCombo.getItem();
  }
}
