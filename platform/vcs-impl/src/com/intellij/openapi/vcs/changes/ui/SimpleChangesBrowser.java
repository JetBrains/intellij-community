/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SimpleChangesBrowser extends ChangesBrowserBase {
  private final List<Change> myChanges = new ArrayList<>();
  @Nullable private ChangeNodeDecorator myChangeNodeDecorator;

  public SimpleChangesBrowser(@NotNull Project project,
                              @NotNull Collection<? extends Change> changes) {
    this(project, false, false);
    setChangesToDisplay(changes);
  }

  public SimpleChangesBrowser(@NotNull Project project,
                              boolean showCheckboxes,
                              boolean highlightProblems) {
    super(project, showCheckboxes, highlightProblems);
    init();
  }


  @NotNull
  @Override
  protected DefaultTreeModel buildTreeModel(boolean showFlatten) {
    return TreeModelBuilder.buildFromChanges(myProject, showFlatten, myChanges, myChangeNodeDecorator);
  }


  public void setChangesToDisplay(@NotNull Collection<? extends Change> changes) {
    myChanges.clear();
    myChanges.addAll(changes);
    myViewer.rebuildTree();
  }

  public void setChangeNodeDecorator(@Nullable ChangeNodeDecorator value) {
    myChangeNodeDecorator = value;
    myViewer.rebuildTree();
  }

  public void setIncludedChanges(@NotNull Collection<? extends Change> changes) {
    myViewer.setIncludedChanges(changes);
  }


  @NotNull
  public List<Change> getAllChanges() {
    return VcsTreeModelData.all(myViewer).userObjects(Change.class);
  }

  @NotNull
  public List<Change> getSelectedChanges() {
    return VcsTreeModelData.selected(myViewer).userObjects(Change.class);
  }

  @NotNull
  public List<Change> getIncludedChanges() {
    return VcsTreeModelData.included(myViewer).userObjects(Change.class);
  }
}