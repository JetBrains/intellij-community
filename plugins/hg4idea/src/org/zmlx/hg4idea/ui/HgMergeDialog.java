// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.command.HgHeadsCommand;
import org.zmlx.hg4idea.command.HgTagBranch;
import org.zmlx.hg4idea.command.HgWorkingCopyRevisionsCommand;
import org.zmlx.hg4idea.util.HgBranchesAndTags;
import org.zmlx.hg4idea.util.HgUiUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class HgMergeDialog extends DialogWrapper {

  private final Project project;

  private JRadioButton revisionOption;
  private JTextField revisionTxt;
  private JRadioButton branchOption;
  private JRadioButton tagOption;
  private JRadioButton bookmarkOption;
  private JComboBox branchSelector;
  private JComboBox tagSelector;
  private JComboBox bookmarkSelector;
  private JPanel contentPanel;
  private HgRepositorySelectorComponent hgRepositorySelectorComponent;
  private JRadioButton otherHeadRadioButton;
  private JLabel otherHeadLabel;

  private HgRevisionNumber otherHead;
  private Map<VirtualFile, Collection<HgTagBranch>> branchesForRepos;
  private Map<VirtualFile, Collection<HgTagBranch>> tagsForRepos;
  private Map<VirtualFile, Collection<HgTagBranch>> bookmarksForRepos;

  public HgMergeDialog(Project project,
                       Collection<VirtualFile> roots,
                       @Nullable VirtualFile selectedRepo, HgBranchesAndTags branchesAndTags) {
    super(project, false);
    this.project = project;
    branchesForRepos = branchesAndTags.getBranchesForRepos();
    tagsForRepos = branchesAndTags.getTagsForRepos();
    bookmarksForRepos = branchesAndTags.getBookmarksForRepos();
    setRoots(roots, selectedRepo);
    hgRepositorySelectorComponent.setTitle("Select repository to merge");
    hgRepositorySelectorComponent.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateRepository();
      }
    });

    ChangeListener changeListener = new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        updateOptions();
      }
    };
    branchOption.addChangeListener(changeListener);
    tagOption.addChangeListener(changeListener);
    bookmarkOption.addChangeListener(changeListener);
    revisionOption.addChangeListener(changeListener);
    otherHeadRadioButton.addChangeListener(changeListener);
    setTitle("Merge");
    init();
  }

  public void setRoots(Collection<VirtualFile> repos, @Nullable VirtualFile selectedRepo) {
    hgRepositorySelectorComponent.setRoots(repos);
    hgRepositorySelectorComponent.setSelectedRoot(selectedRepo);
    updateRepository();
  }

  public VirtualFile getRepository() {
    return hgRepositorySelectorComponent.getRepository();
  }

  public HgTagBranch getBranch() {
    return branchOption.isSelected() ? (HgTagBranch) branchSelector.getSelectedItem() : null;
  }

  public HgTagBranch getTag() {
    return tagOption.isSelected() ? (HgTagBranch) tagSelector.getSelectedItem() : null;
  }

  public HgTagBranch getBookmark() {
    return bookmarkOption.isSelected() ? (HgTagBranch)bookmarkSelector.getSelectedItem() : null;
  }

  public String getRevision() {
    return revisionOption.isSelected() ? revisionTxt.getText() : null;
  }

  public HgRevisionNumber getOtherHead() {
    return otherHeadRadioButton.isSelected() ? otherHead : null;
  }

  private void updateRepository() {
    VirtualFile repo = getRepository();
    HgUiUtil.loadContentToDialog(repo, branchesForRepos, branchSelector);
    HgUiUtil.loadContentToDialog(repo, tagsForRepos, tagSelector);
    HgUiUtil.loadContentToDialog(repo, bookmarksForRepos, bookmarkSelector);
    loadHeads(repo);
  }

  private void updateOptions() {
    revisionTxt.setEnabled(revisionOption.isSelected());
    branchSelector.setEnabled(branchOption.isSelected());
    tagSelector.setEnabled(tagOption.isSelected());
    bookmarkSelector.setEnabled(bookmarkOption.isSelected());
  }

  private void loadHeads(final VirtualFile root) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        final List<HgRevisionNumber> heads = new HgHeadsCommand(project, root).execute();
        if (heads.size() != 2) {
          disableOtherHeadsChoice();
          return;
        }

        HgRevisionNumber currentParent = new HgWorkingCopyRevisionsCommand(project).identify(root).getFirst();
        for (Iterator<HgRevisionNumber> it = heads.iterator(); it.hasNext(); ) {
          final HgRevisionNumber rev = it.next();
          if (rev.getRevisionNumber().equals(currentParent.getRevisionNumber())) {
            it.remove();
          }
        }

        if (heads.size() == 1) {
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            @Override
            public void run() {
              otherHeadRadioButton.setVisible(true);
              otherHeadLabel.setVisible(true);
              otherHead = heads.get(0);
              otherHeadLabel.setText("  " + otherHead.asString());
            }
          });
        }
        else {
          //apparently we are not at one of the heads
          disableOtherHeadsChoice();
        }
      }
    });
  }

  private void disableOtherHeadsChoice() {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        otherHeadLabel.setVisible(false);
        otherHeadRadioButton.setVisible(false);
      }
    });
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return contentPanel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }
}
