// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.CommonBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.RollbackUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.*;

/**
 * @author max
 */
public class RollbackChangesDialog extends DialogWrapper {
  public static final String DELETE_LOCALLY_ADDED_FILES_KEY = "delete.locally.added.files";
  private final Project myProject;
  private final LocalChangesBrowser myBrowser;
  private final boolean myInvokedFromModalContext;
  private final JCheckBox myDeleteLocallyAddedFiles;
  private final ChangeInfoCalculator myInfoCalculator;
  private final CommitLegendPanel myCommitLegendPanel;
  private final Runnable myListChangeListener;
  private String myOperationName;

  public static void rollbackChanges(final Project project, final Collection<? extends Change> changes) {
    final ChangeListManagerEx manager = (ChangeListManagerEx) ChangeListManager.getInstance(project);

    if (changes.isEmpty()) {
      showNoChangesDialog(project);
      return;
    }

    final Set<LocalChangeList> lists = new THashSet<>();
    lists.addAll(manager.getAffectedLists(changes));

    new RollbackChangesDialog(project, ContainerUtil.newArrayList(lists), new ArrayList<>(changes)).show();
  }

  public static void rollbackChanges(final Project project, final LocalChangeList changeList) {
    List<Change> changes = new ArrayList<>(changeList.getChanges());

    if (changes.isEmpty()) {
      showNoChangesDialog(project);
      return;
    }

    new RollbackChangesDialog(project, Collections.singletonList(changeList), Collections.emptyList()).show();
  }

  private static void showNoChangesDialog(Project project) {
    String operationName = UIUtil.removeMnemonic(RollbackUtil.getRollbackOperationName(project));
    Messages.showWarningDialog(project, VcsBundle.message("commit.dialog.no.changes.detected.text"),
                               VcsBundle.message("changes.action.rollback.nothing", operationName));
  }

  public RollbackChangesDialog(final Project project,
                               final List<LocalChangeList> changeLists,
                               final List<Change> changes) {
    super(project, true);

    myProject = project;
    myInvokedFromModalContext = LaterInvocator.isInModalContext();

    myInfoCalculator = new ChangeInfoCalculator();
    myCommitLegendPanel = new CommitLegendPanel(myInfoCalculator);
    myListChangeListener = new Runnable() {
      @Override
      public void run() {
        if (myBrowser != null) {
          List<Change> allChanges = myBrowser.getAllChanges();
          Collection<Change> includedChanges = myBrowser.getIncludedChanges();

          myInfoCalculator.update(allChanges, ContainerUtil.newArrayList(includedChanges));
          myCommitLegendPanel.update();

          boolean hasNewFiles = ContainerUtil.exists(includedChanges, change -> change.getType() == Change.Type.NEW);
          myDeleteLocallyAddedFiles.setEnabled(hasNewFiles);
        }
      }
    };
    myBrowser = new LocalChangesBrowser(project);
    myBrowser.setIncludedChanges(changes);
    myBrowser.setChangeLists(changeLists);
    myBrowser.setInclusionChangedListener(myListChangeListener);
    Disposer.register(getDisposable(), myBrowser);

    myOperationName = operationNameByChanges(project, myBrowser.getAllChanges());
    myBrowser.setToggleActionTitle("&Include in " + myOperationName.toLowerCase());
    setOKButtonText(myOperationName);

    myOperationName = UIUtil.removeMnemonic(myOperationName);
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
