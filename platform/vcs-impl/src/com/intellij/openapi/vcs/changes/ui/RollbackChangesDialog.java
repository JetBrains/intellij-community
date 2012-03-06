/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class RollbackChangesDialog extends DialogWrapper {
  private final Project myProject;
  private final boolean myRefreshSynchronously;
  private final Runnable myAfterVcsRefreshInAwt;
  private final MultipleChangeListBrowser myBrowser;
  @Nullable private JCheckBox myDeleteLocallyAddedFiles;
  private final ChangeInfoCalculator myInfoCalculator;
  private final CommitLegendPanel myCommitLegendPanel;
  private Runnable myListChangeListener;

  public static void rollbackChanges(final Project project, final Collection<Change> changes) {
    rollbackChanges(project, changes, true);
  }

  public static void rollbackChanges(final Project project, final Collection<Change> changes, boolean refreshSynchronously) {
    rollbackChanges(project, changes, refreshSynchronously, null);
  }

  public static void rollbackChanges(final Project project, final Collection<Change> changes, boolean refreshSynchronously,
                                     final Runnable afterVcsRefreshInAwt) {
    final ChangeListManagerEx manager = (ChangeListManagerEx) ChangeListManager.getInstance(project);

    if (changes.isEmpty()) {
      Messages.showWarningDialog(project, VcsBundle.message("commit.dialog.no.changes.detected.text"),
                                 VcsBundle.message("commit.dialog.no.changes.detected.title"));
      return;
    }

    final ArrayList<Change> validChanges = new ArrayList<Change>();
    final Set<LocalChangeList> lists = new THashSet<LocalChangeList>();
    lists.addAll(manager.getInvolvedListsFilterChanges(changes, validChanges));

    rollback(project, new ArrayList<LocalChangeList>(lists), validChanges, refreshSynchronously, afterVcsRefreshInAwt);
  }

  public static void rollback(final Project project,
                              final List<LocalChangeList> changeLists,
                              final List<Change> changes,
                              final boolean refreshSynchronously, final Runnable afterVcsRefreshInAwt) {
    new RollbackChangesDialog(project, changeLists, changes, refreshSynchronously, afterVcsRefreshInAwt).show();
  }

  public RollbackChangesDialog(final Project project,
                               List<LocalChangeList> changeLists,
                               final List<Change> changes,
                               final boolean refreshSynchronously, final Runnable afterVcsRefreshInAwt) {
    super(project, true);

    myProject = project;
    myRefreshSynchronously = refreshSynchronously;
    myAfterVcsRefreshInAwt = afterVcsRefreshInAwt;

    myInfoCalculator = new ChangeInfoCalculator();
    myCommitLegendPanel = new CommitLegendPanel(myInfoCalculator);
    myListChangeListener = new Runnable() {
      @Override
      public void run() {
        if (myBrowser != null) {
          myInfoCalculator.update(changes, new ArrayList<Change>(myBrowser.getChangesIncludedInAllLists()));
          myCommitLegendPanel.update();
        }
      }
    };
    myBrowser = new MultipleChangeListBrowser(project, changeLists, changes, null, true, true, myListChangeListener, myListChangeListener);
    myBrowser.setToggleActionTitle("Include in rollback");

    setCancelButtonText(CommonBundle.getCloseButtonText());
    setOKButtonText(VcsBundle.message("changes.action.rollback.text"));
    setTitle(VcsBundle.message("changes.action.rollback.title"));

    Set<AbstractVcs> affectedVcs = new HashSet<AbstractVcs>();
    for (Change c : changes) {
      final AbstractVcs vcs = ChangesUtil.getVcsForChange(c, project);
      if (vcs != null) {
        // vcs may be null if we have turned off VCS integration and are in process of refreshing
        affectedVcs.add(vcs);
      }
    }
    if (affectedVcs.size() == 1) {
      AbstractVcs vcs = (AbstractVcs)affectedVcs.toArray()[0];
      final RollbackEnvironment rollbackEnvironment = vcs.getRollbackEnvironment();
      if (rollbackEnvironment != null) {
        String rollbackOperationName = rollbackEnvironment.getRollbackOperationName();
        int pos = rollbackOperationName.indexOf(UIUtil.MNEMONIC);
        if (pos >= 0) {
          setOKButtonMnemonic(Character.toUpperCase(rollbackOperationName.charAt(pos + 1)));
          rollbackOperationName = rollbackOperationName.replace(Character.toString(UIUtil.MNEMONIC), "");
        }
        setTitle(VcsBundle.message("changes.action.rollback.custom.title", rollbackOperationName).replace("_", ""));
        setOKButtonText(rollbackOperationName);
      }
    }

    for (Change c : changes) {
      if (c.getType() == Change.Type.NEW) {
        myDeleteLocallyAddedFiles = new JCheckBox(VcsBundle.message("changes.checkbox.delete.locally.added.files"));
        break;
      }
    }

    init();
    myListChangeListener.run();
  }

  @Override
  protected void dispose() {
    super.dispose();
    myBrowser.dispose();
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    new RollbackWorker(myProject, myRefreshSynchronously).doRollback(myBrowser.getChangesIncludedInAllLists(),
                                                                     myDeleteLocallyAddedFiles != null && myDeleteLocallyAddedFiles.isSelected(),
                                                                     myAfterVcsRefreshInAwt, null);
  }

  @Nullable
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints gb =
      new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                             new Insets(1, 1, 1, 1), 0, 0);

    JComponent browserHeader = myBrowser.getHeaderPanel();
    myBrowser.remove(browserHeader);
    gb.fill = GridBagConstraints.HORIZONTAL;
    gb.weightx = 1;
    gb.gridwidth = 2;
    panel.add(browserHeader, gb);

    final JPanel border = new JPanel(new BorderLayout());
    border.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
    border.add(myBrowser, BorderLayout.CENTER);
    gb.fill = GridBagConstraints.BOTH;
    gb.weighty = 1;
    gb.gridwidth = 1;
    gb.gridheight = 3;
    ++ gb.gridy;
    panel.add(border, gb);

    final JPanel commitLegendPanel = myCommitLegendPanel.getComponent();
    commitLegendPanel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
    gb.fill = GridBagConstraints.NONE;
    gb.weightx = 0;
    gb.weighty = 0;
    gb.gridheight = 1;
    ++ gb.gridx;
    panel.add(commitLegendPanel, gb);

    if (myDeleteLocallyAddedFiles != null) {
      ++ gb.gridy;
      panel.add(new JSeparator(), gb);
      ++ gb.gridy;
      panel.add(myDeleteLocallyAddedFiles, gb);
    }

    return panel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myBrowser.getPreferredFocusedComponent();
  }

  protected String getDimensionServiceKey() {
    return "RollbackChangesDialog";
  }
}
