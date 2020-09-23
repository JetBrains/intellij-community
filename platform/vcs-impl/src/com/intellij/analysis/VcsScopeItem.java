// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis;

import com.intellij.analysis.dialog.ModelScopeItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class VcsScopeItem implements ModelScopeItem {
  private final ChangeListManager myChangeListManager;
  private final DefaultComboBoxModel<LocalChangeList> myModel;
  private final Project myProject;

  @Nullable
  public static VcsScopeItem createIfHasVCS(Project project) {
    if (ChangeListManager.getInstance(project).getAffectedFiles().isEmpty()) {
      return null;
    }

    return new VcsScopeItem(project);
  }

  public VcsScopeItem(Project project) {
    myProject = project;
    myChangeListManager = ChangeListManager.getInstance(project);
    assert !myChangeListManager.getAffectedFiles().isEmpty();

    if (myChangeListManager.areChangeListsEnabled()) {
      myModel = new DefaultComboBoxModel<>();
      myModel.addElement(null);
      List<LocalChangeList> changeLists = myChangeListManager.getChangeLists();
      for (LocalChangeList changeList : changeLists) {
        myModel.addElement(changeList);
      }
    }
    else {
      myModel = null;
    }
  }

  @Override
  public AnalysisScope getScope() {
    ChangeList changeList = myModel != null ? (ChangeList)myModel.getSelectedItem() : null;

    List<VirtualFile> files;
    if (changeList == null) {
      files = myChangeListManager.getAffectedFiles();
    }
    else {
      LocalChangeList list = myChangeListManager.findChangeList(changeList.getName());
      if (list != null) {
        files = ChangesUtil.getAfterRevisionsFiles(list.getChanges().stream())
          .collect(Collectors.toList());
      }
      else {
        files = Collections.emptyList();
      }
    }
    return new AnalysisScope(myProject, new HashSet<>(files));
  }

  @Nullable
  public DefaultComboBoxModel<LocalChangeList> getChangeListsModel() {
    return myModel;
  }
}