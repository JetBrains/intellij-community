// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.CommonBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.RollbackUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RollbackChangesDialog extends DialogWrapper {
  public static final String DELETE_LOCALLY_ADDED_FILES_KEY = "delete.locally.added.files";
  private final Project myProject;
  private final LocalChangesBrowser myBrowser;
  private final boolean myInvokedFromModalContext;
  private final JCheckBox myDeleteLocallyAddedFiles;
  private final ChangeInfoCalculator myInfoCalculator;
  private final CommitLegendPanel myCommitLegendPanel;
  private final Runnable myListChangeListener;
  private final @Nls String myOperationName;

  public static void rollbackChanges(@NotNull Project project, @NotNull Collection<? extends Change> changes) {
    LocalChangesBrowser browser;

    ChangeListManagerEx changeListManager = ChangeListManagerEx.getInstanceEx(project);
    if (changeListManager.areChangeListsEnabled()) {
      Collection<LocalChangeList> lists = changeListManager.getAffectedLists(changes);
      browser = new LocalChangesBrowser.SelectedChangeLists(project, lists);
    }
    else {
      browser = new LocalChangesBrowser.AllChanges(project);
    }
    browser.setIncludedChanges(changes);
    browser.getViewer().resetTreeState(); // set initial selection by included changes

    showRollbackDialog(project, browser);
  }

  public static void rollbackChanges(@NotNull Project project) {
    LocalChangesBrowser browser;

    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if (changeListManager.areChangeListsEnabled()) {
      List<LocalChangeList> lists = Collections.singletonList(changeListManager.getDefaultChangeList());
      browser = new LocalChangesBrowser.SelectedChangeLists(project, lists);
    }
    else {
      browser = new LocalChangesBrowser.AllChanges(project);
    }

    showRollbackDialog(project, browser);
  }

  public static void rollbackChanges(final Project project, final LocalChangeList changeList) {
    List<LocalChangeList> lists = Collections.singletonList(changeList);
    LocalChangesBrowser browser = new LocalChangesBrowser.SelectedChangeLists(project, lists);
    showRollbackDialog(project, browser);
  }

  private static void showRollbackDialog(@NotNull Project project, @NotNull LocalChangesBrowser browser) {
    if (browser.getAllChanges().isEmpty()) {
      String operationName = UIUtil.removeMnemonic(RollbackUtil.getRollbackOperationName(project));
      Messages.showWarningDialog(project, VcsBundle.message("commit.dialog.no.changes.detected.text"),
                                 VcsBundle.message("changes.action.rollback.nothing", operationName));
      return;
    }

    new RollbackChangesDialog(project, browser).show();
  }

  private RollbackChangesDialog(@NotNull Project project, @NotNull LocalChangesBrowser browser) {
    super(project, true);

    myProject = project;
    myBrowser = browser;
    myInvokedFromModalContext = LaterInvocator.isInModalContext();

    myInfoCalculator = new ChangeInfoCalculator();
    myCommitLegendPanel = new CommitLegendPanel(myInfoCalculator);
    myListChangeListener = new Runnable() {
      @Override
      public void run() {
        List<Change> allChanges = myBrowser.getAllChanges();
        Collection<Change> includedChanges = myBrowser.getIncludedChanges();

        myInfoCalculator.update(allChanges, new ArrayList<>(includedChanges));
        myCommitLegendPanel.update();

        boolean hasNewFiles = ContainerUtil.exists(includedChanges, change -> change.getType() == Change.Type.NEW);
        myDeleteLocallyAddedFiles.setEnabled(hasNewFiles);
      }
    };
    myBrowser.setInclusionChangedListener(myListChangeListener);
    Disposer.register(getDisposable(), myBrowser);

    String operationName = operationNameByChanges(project, myBrowser.getAllChanges());
    setOKButtonText(operationName);

    myOperationName = UIUtil.removeMnemonic(operationName);
    myBrowser.setToggleActionTitle(VcsBundle.message("changes.action.include.in.operation.name", StringUtil.toLowerCase(myOperationName)));
    setTitle(VcsBundle.message("changes.action.rollback.custom.title", myOperationName));
    setCancelButtonText(CommonBundle.getCloseButtonText());

    myDeleteLocallyAddedFiles = new JCheckBox(VcsBundle.message("changes.checkbox.delete.locally.added.files"));
    myDeleteLocallyAddedFiles.setSelected(PropertiesComponent.getInstance().isTrueValue(DELETE_LOCALLY_ADDED_FILES_KEY));
    myDeleteLocallyAddedFiles.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        PropertiesComponent.getInstance().setValue(DELETE_LOCALLY_ADDED_FILES_KEY, myDeleteLocallyAddedFiles.isSelected());
      }
    });

    init();
    myListChangeListener.run();
  }

  @NotNull
  @Nls(capitalization = Nls.Capitalization.Title)
  public static String operationNameByChanges(@NotNull Project project, @NotNull Collection<? extends Change> changes) {
    return RollbackUtil.getRollbackOperationName(ChangesUtil.getAffectedVcses(changes, project));
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    RollbackWorker worker = new RollbackWorker(myProject, myOperationName, myInvokedFromModalContext);
    worker.doRollback(myBrowser.getIncludedChanges(), myDeleteLocallyAddedFiles.isSelected());
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints gb =
      new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                             JBUI.insets(1), 0, 0);

    gb.fill = GridBagConstraints.HORIZONTAL;
    gb.weightx = 1;

    final JPanel border = new JPanel(new BorderLayout());
    border.setBorder(JBUI.Borders.emptyTop(2));
    border.add(myBrowser, BorderLayout.CENTER);
    gb.fill = GridBagConstraints.BOTH;
    gb.weighty = 1;
    ++gb.gridy;
    panel.add(border, gb);

    final JComponent commitLegendPanel = myCommitLegendPanel.getComponent();
    commitLegendPanel.setBorder(JBUI.Borders.emptyLeft(4));
    gb.fill = GridBagConstraints.NONE;
    gb.weightx = 0;
    gb.weighty = 0;
    ++gb.gridy;
    panel.add(commitLegendPanel, gb);

    ++gb.gridy;
    panel.add(myDeleteLocallyAddedFiles, gb);

    return panel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myBrowser.getPreferredFocusedComponent();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "RollbackChangesDialog";
  }
}
