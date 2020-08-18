// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

import static org.zmlx.hg4idea.util.HgUtil.TIP_REFERENCE;

public class HgCommonDialogWithChoices extends DialogWrapper {


  private JPanel contentPanel;
  private JRadioButton branchOption;
  private JRadioButton revisionOption;
  private JRadioButton tagOption;
  private JRadioButton bookmarkOption;
  private JTextField revisionTxt;
  protected JCheckBox cleanCbx;
  private JComboBox branchSelector;
  private JComboBox tagSelector;
  private JComboBox bookmarkSelector;
  protected HgRepositorySelectorComponent hgRepositorySelectorComponent;
  protected JPanel myBranchesBorderPanel;

  public HgCommonDialogWithChoices(@NotNull Project project, @NotNull Collection<HgRepository> repositories, @Nullable HgRepository selectedRepo) {
    super(project, false);
    hgRepositorySelectorComponent.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateRepository();
      }
    });

    ChangeListener changeListener = new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        update();
      }
    };
    branchOption.addChangeListener(changeListener);
    tagOption.addChangeListener(changeListener);
    bookmarkOption.addChangeListener(changeListener);
    revisionOption.addChangeListener(changeListener);
    cleanCbx.setVisible(false);
    setRoots(repositories, selectedRepo);
    init();
  }

  public void setRoots(Collection<HgRepository> repos,
                       @Nullable HgRepository selectedRepo) {
    hgRepositorySelectorComponent.setRoots(repos);
    hgRepositorySelectorComponent.setSelectedRoot(selectedRepo);
    updateRepository();
  }

  public HgRepository getRepository() {
    return hgRepositorySelectorComponent.getRepository();
  }

  private String getTag() {
    return (String)tagSelector.getSelectedItem();
  }

  public boolean isTagSelected() {
    return tagOption.isSelected();
  }

  private String getBranch() {
    return (String)branchSelector.getSelectedItem();
  }

  public boolean isBranchSelected() {
    return branchOption.isSelected();
  }

  private boolean isRevisionSelected() {
    return revisionOption.isSelected();
  }

  private String getBookmark() {
    return (String)bookmarkSelector.getSelectedItem();
  }

  public boolean isBookmarkSelected() {
    return bookmarkOption.isSelected();
  }

  private String getRevision() {
    return revisionTxt.getText();
  }

  private void update() {
    revisionTxt.setEnabled(revisionOption.isSelected());
    branchSelector.setEnabled(branchOption.isSelected());
    tagSelector.setEnabled(tagOption.isSelected());
    bookmarkSelector.setEnabled(bookmarkOption.isSelected());
  }

  private void updateRepository() {
    HgRepository repo = hgRepositorySelectorComponent.getRepository();
    branchSelector.setModel(new DefaultComboBoxModel(repo.getOpenedBranches().toArray()));
    DefaultComboBoxModel tagComboBoxModel = new DefaultComboBoxModel(HgUtil.getSortedNamesWithoutHashes(repo.getTags()).toArray());
    tagComboBoxModel
      .addElement(TIP_REFERENCE);    //HgRepository does not store 'tip' tag because it is internal and not included in tags file
    tagSelector.setModel(tagComboBoxModel);
    bookmarkSelector.setModel(new DefaultComboBoxModel(HgUtil.getSortedNamesWithoutHashes(repo.getBookmarks()).toArray()));
    update();
  }

  @Override
  protected JComponent createCenterPanel() {
    return contentPanel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  protected void createUIComponents() {
  }

  @NonNls
  public String getTargetValue() {
    return isBranchSelected()
           ? "branch(\"" + getBranch() + "\")"
           : isBookmarkSelected()
             ? "bookmark(\"" + getBookmark() + "\")"
             : isTagSelected() ? "tag(\"" + getTag() + "\")" : "\"" + getRevision() + "\"";
  }

  @Override
  protected ValidationInfo doValidate() {
    String message = HgBundle.message("specify.name.or.revision");
    return isRevisionSelected() && StringUtil.isEmptyOrSpaces(getRevision()) ? new ValidationInfo(message, myBranchesBorderPanel) : null;
  }
}
