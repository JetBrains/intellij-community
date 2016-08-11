/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.CommonBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
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
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class RollbackChangesDialog extends DialogWrapper {
  public static final String DELETE_LOCALLY_ADDED_FILES_KEY = "delete.locally.added.files";
  private final Project myProject;
  private final boolean myRefreshSynchronously;
  private final Runnable myAfterVcsRefreshInAwt;
  private final ChangesBrowser myBrowser;
  private final boolean myInvokedFromModalContext;
  private final JCheckBox myDeleteLocallyAddedFiles;
  private final ChangeInfoCalculator myInfoCalculator;
  private final CommitLegendPanel myCommitLegendPanel;
  private Runnable myListChangeListener;
  private String myOperationName;

  public static void rollbackChanges(final Project project, final Collection<Change> changes) {
    rollbackChanges(project, changes, true, null);
  }

  public static void rollbackChanges(final Project project, final Collection<Change> changes, boolean refreshSynchronously,
                                     final Runnable afterVcsRefreshInAwt) {
    final ChangeListManagerEx manager = (ChangeListManagerEx) ChangeListManager.getInstance(project);

    if (changes.isEmpty()) {
      showNoChangesDialog(project);
      return;
    }

    final ArrayList<Change> validChanges = new ArrayList<>();
    final Set<LocalChangeList> lists = new THashSet<>();
    lists.addAll(manager.getInvolvedListsFilterChanges(changes, validChanges));

    new RollbackChangesDialog(project, ContainerUtil.newArrayList(lists), validChanges, refreshSynchronously, afterVcsRefreshInAwt).show();
  }

  public static void rollbackChanges(final Project project, final LocalChangeList changeList) {
    List<Change> changes = new ArrayList<>(changeList.getChanges());

    if (changes.isEmpty()) {
      showNoChangesDialog(project);
      return;
    }

    new RollbackChangesDialog(project, Collections.singletonList(changeList), Collections.<Change>emptyList(), true, null).show();
  }

  private static void showNoChangesDialog(Project project) {
    String operationName = UIUtil.removeMnemonic(RollbackUtil.getRollbackOperationName(project));
    Messages.showWarningDialog(project, VcsBundle.message("commit.dialog.no.changes.detected.text"),
                               VcsBundle.message("changes.action.rollback.nothing", operationName));
  }

  public RollbackChangesDialog(final Project project,
                               final List<LocalChangeList> changeLists,
                               final List<Change> changes,
                               final boolean refreshSynchronously, final Runnable afterVcsRefreshInAwt) {
    super(project, true);

    myProject = project;
    myRefreshSynchronously = refreshSynchronously;
    myAfterVcsRefreshInAwt = afterVcsRefreshInAwt;
    myInvokedFromModalContext = LaterInvocator.isInModalContext();

    myInfoCalculator = new ChangeInfoCalculator();
    myCommitLegendPanel = new CommitLegendPanel(myInfoCalculator);
    myListChangeListener = new Runnable() {
      @Override
      public void run() {
        if (myBrowser != null) {
          // We could not utilize "myBrowser.getViewer().getChanges()" here (to get all changes) as currently it is not recursive.
          List<Change> allChanges = getAllChanges(changeLists);
          Collection<Change> includedChanges = myBrowser.getViewer().getIncludedChanges();

          myInfoCalculator.update(allChanges, ContainerUtil.newArrayList(includedChanges));
          myCommitLegendPanel.update();

          boolean hasNewFiles = ContainerUtil.exists(includedChanges, new Condition<Change>() {
            @Override
            public boolean value(Change change) {
              return change.getType() == Change.Type.NEW;
            }
          });
          myDeleteLocallyAddedFiles.setEnabled(hasNewFiles);
        }
      }
    };
    myBrowser =
      new ChangesBrowser(project, changeLists, changes, null, true, true, myListChangeListener, ChangesBrowser.MyUseCase.LOCAL_CHANGES,
                         null) {
        @NotNull
        @Override
        protected DefaultTreeModel buildTreeModel(List<Change> changes, ChangeNodeDecorator changeNodeDecorator, boolean showFlatten) {
          TreeModelBuilder builder = new TreeModelBuilder(myProject, showFlatten);
          // Currently we do not explicitly utilize passed "changeNodeDecorator" instance (which is defined by
          // "ChangesBrowser.MyUseCase.LOCAL_CHANGES" parameter passed to "ChangesBrowser"). But correct node decorator will still be set
          // in "TreeModelBuilder.setChangeLists()".
          return builder.setChangeLists(changeLists).build();
        }
      };
    Disposer.register(getDisposable(), myBrowser);

    myOperationName = operationNameByChanges(project, getAllChanges(changeLists));
    setOKButtonText(myOperationName);

    myOperationName = UIUtil.removeMnemonic(myOperationName);
    setTitle(VcsBundle.message("changes.action.rollback.custom.title", myOperationName));
    setCancelButtonText(CommonBundle.getCloseButtonText());
    myBrowser.setToggleActionTitle("&Include in " + myOperationName.toLowerCase());

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
  public static String operationNameByChanges(@NotNull Project project, @NotNull Collection<Change> changes) {
    return RollbackUtil.getRollbackOperationName(ChangesUtil.getAffectedVcses(changes, project));
  }

  @NotNull
  private static List<Change> getAllChanges(@NotNull List<? extends ChangeList> changeLists) {
    List<Change> result = ContainerUtil.newArrayList();

    for (ChangeList list : changeLists) {
      result.addAll(list.getChanges());
    }

    return result;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    RollbackWorker worker = new RollbackWorker(myProject, myOperationName, myInvokedFromModalContext);
    worker.doRollback(myBrowser.getViewer().getIncludedChanges(), myDeleteLocallyAddedFiles.isSelected(),
                      myAfterVcsRefreshInAwt, null);
  }

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

  public JComponent getPreferredFocusedComponent() {
    return myBrowser.getPreferredFocusedComponent();
  }

  protected String getDimensionServiceKey() {
    return "RollbackChangesDialog";
  }
}
