// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis;

import com.intellij.analysis.dialog.ModelScopeItem;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class VcsScopeItem implements ModelScopeItem {
  private final ChangeListManager myChangeListManager;
  private final DefaultComboBoxModel<String> myModel;
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

    myModel = new DefaultComboBoxModel<>();
    myModel.addElement(getAll());
    final List<? extends ChangeList> changeLists = myChangeListManager.getChangeListsCopy();
    for (ChangeList changeList : changeLists) {
      myModel.addElement(changeList.getName());
    }
  }

  @Override
  public AnalysisScope getScope() {
    Object selectedItem = myModel.getSelectedItem();
    if (selectedItem == null)
      return null;

    List<VirtualFile> files;
    if (selectedItem == getAll()) {
      files = myChangeListManager.getAffectedFiles();
    }
    else {
      files = myChangeListManager
        .getChangeListsCopy()
        .stream()
        .filter(l -> Comparing.strEqual(l.getName(), (String)selectedItem))
        .flatMap(l -> ChangesUtil.getAfterRevisionsFiles(l.getChanges().stream()))
        .collect(Collectors.toList());
    }
    return new AnalysisScope(myProject, new HashSet<>(files));
  }

  public DefaultComboBoxModel<String> getChangeListsModel() {
    return myModel;
  }

  private static String getAll() {
    return CodeInsightBundle.message("scope.option.uncommitted.files.all.changelists.choice");
  }
}