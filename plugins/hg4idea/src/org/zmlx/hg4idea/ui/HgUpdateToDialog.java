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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.command.HgTagBranch;
import org.zmlx.hg4idea.util.HgBranchesAndTags;
import org.zmlx.hg4idea.util.HgUiUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Map;

public class HgUpdateToDialog extends DialogWrapper {

  private final Project project;

  private JPanel contentPanel;
  private JRadioButton branchOption;
  private JRadioButton revisionOption;
  private JRadioButton tagOption;
  private JRadioButton bookmarkOption;
  private JTextField revisionTxt;
  private JCheckBox cleanCbx;
  private JComboBox branchSelector;
  private JComboBox tagSelector;
  private JComboBox bookmarkSelector;
  private HgRepositorySelectorComponent hgRepositorySelectorComponent;
  @NotNull private Map<VirtualFile, Collection<HgTagBranch>> branchesForRepos;
  @NotNull private Map<VirtualFile, Collection<HgTagBranch>> tagsForRepos;
  @NotNull private Map<VirtualFile, Collection<HgTagBranch>> bookmarksForRepos;

  public HgUpdateToDialog(Project project) {
    super(project, false);
    this.project = project;
    hgRepositorySelectorComponent.setTitle("Select repository to switch");
    hgRepositorySelectorComponent.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateRepository();
      }
    });

    ChangeListener changeListener = new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        update();
      }
    };
    branchOption.addChangeListener(changeListener);
    tagOption.addChangeListener(changeListener);
    bookmarkOption.addChangeListener(changeListener);
    revisionOption.addChangeListener(changeListener);

    setTitle("Switch working directory");
    init();
  }

  public void setRoots(Collection<VirtualFile> repos,
                       @Nullable VirtualFile selectedRepo, HgBranchesAndTags branchesAndTags) {
    hgRepositorySelectorComponent.setRoots(repos);
    branchesForRepos = branchesAndTags.getBranchesForRepos();
    tagsForRepos = branchesAndTags.getTagsForRepos();
    bookmarksForRepos = branchesAndTags.getBookmarksForRepos();
    hgRepositorySelectorComponent.setSelectedRoot(selectedRepo);
    updateRepository();
  }

  public VirtualFile getRepository() {
    return hgRepositorySelectorComponent.getRepository();
  }

  public HgTagBranch getTag() {
    return (HgTagBranch) tagSelector.getSelectedItem();
  }

  public boolean isTagSelected() {
    return tagOption.isSelected();
  }

  public HgTagBranch getBranch() {
    return (HgTagBranch) branchSelector.getSelectedItem();
  }

  public boolean isBranchSelected() {
    return branchOption.isSelected();
  }

  public HgTagBranch getBookmark() {
    return (HgTagBranch)bookmarkSelector.getSelectedItem();
  }

  public boolean isBookmarkSelected() {
    return bookmarkOption.isSelected();
  }

  public String getRevision() {
    return revisionTxt.getText();
  }

  public boolean isRevisionSelected() {
    return revisionOption.isSelected();
  }

  public boolean isRemoveLocalChanges() {
    return cleanCbx.isSelected();
  }

  private void update() {
    revisionTxt.setEnabled(revisionOption.isSelected());
    branchSelector.setEnabled(branchOption.isSelected());
    tagSelector.setEnabled(tagOption.isSelected());
    bookmarkSelector.setEnabled(bookmarkOption.isSelected());
  }

  private void updateRepository() {
    VirtualFile repo = hgRepositorySelectorComponent.getRepository();
    HgUiUtil.loadContentToDialog(repo, branchesForRepos, branchSelector);
    HgUiUtil.loadContentToDialog(repo, tagsForRepos, tagSelector);
    HgUiUtil.loadContentToDialog(repo, bookmarksForRepos, bookmarkSelector);
    update();
  }

  protected JComponent createCenterPanel() {
    return contentPanel;
  }

  @Override
  protected String getHelpId() {
    return "reference.mercurial.switch.working.directory";
  }

  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }
}
