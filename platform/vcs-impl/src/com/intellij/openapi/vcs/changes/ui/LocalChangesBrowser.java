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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import java.util.*;

public class LocalChangesBrowser extends ChangesBrowserBase implements Disposable {
  @NotNull private final ToggleChangeDiffAction myToggleChangeDiffAction;
  @Nullable private Set<String> myChangeListNames;

  public LocalChangesBrowser(@NotNull Project project) {
    super(project, true, true);

    myToggleChangeDiffAction = new ToggleChangeDiffAction();

    ChangeListManager.getInstance(myProject).addChangeListListener(new MyChangeListListener(), this);
    init();

    myViewer.setInclusionHashingStrategy(ChangeListChange.HASHING_STRATEGY);
    myViewer.rebuildTree();
  }

  @Override
  public void dispose() {
  }

  @NotNull
  @Override
  protected List<AnAction> createDiffActions() {
    return ContainerUtil.append(
      super.createDiffActions(),
      myToggleChangeDiffAction
    );
  }


  @NotNull
  @Override
  protected DefaultTreeModel buildTreeModel(boolean showFlatten) {
    List<LocalChangeList> lists = ChangeListManager.getInstance(myProject).getChangeLists();
    if (myChangeListNames != null) {
      lists = ContainerUtil.filter(lists, list -> myChangeListNames.contains(list.getName()));
    }

    return TreeModelBuilder.buildFromChangeLists(myProject, showFlatten, lists);
  }


  public void setIncludedChanges(@NotNull Collection<? extends Change> changes) {
    List<Change> changesToInclude = new ArrayList<>(changes);

    Set<Change> otherChanges = new HashSet<>();
    for (Change change : changes) {
      if (!(change instanceof ChangeListChange)) {
        otherChanges.add(change);
      }
    }

    // include all related ChangeListChange by a simple Change
    if (!otherChanges.isEmpty()) {
      for (Change change : getAllChanges()) {
        if (change instanceof ChangeListChange &&
            otherChanges.contains(change)) {
          changesToInclude.add(change);
        }
      }
    }

    myViewer.setIncludedChanges(changesToInclude);
  }

  public List<Change> getAllChanges() {
    return VcsTreeModelData.all(myViewer).userObjects(Change.class);
  }

  public List<Change> getSelectedChanges() {
    return VcsTreeModelData.selected(myViewer).userObjects(Change.class);
  }

  public List<Change> getIncludedChanges() {
    return VcsTreeModelData.included(myViewer).userObjects(Change.class);
  }


  public void setToggleActionTitle(@Nullable String title) {
    myToggleChangeDiffAction.getTemplatePresentation().setText(title);
  }

  public void setChangeLists(@Nullable List<LocalChangeList> changeLists) {
    myChangeListNames = changeLists != null ? ContainerUtil.map2Set(changeLists, LocalChangeList::getName) : null;
    myViewer.rebuildTree();
  }


  private class ToggleChangeDiffAction extends CheckboxAction implements DumbAware {
    public ToggleChangeDiffAction() {
      super("&Include");
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      Change change = e.getData(VcsDataKeys.CURRENT_CHANGE);
      if (change == null) return false;
      return myViewer.isIncluded(change);
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      Change change = e.getData(VcsDataKeys.CURRENT_CHANGE);
      if (change == null) return;

      if (state) {
        myViewer.includeChange(change);
      }
      else {
        myViewer.excludeChange(change);
      }
    }
  }

  private class MyChangeListListener extends ChangeListAdapter {
    @NotNull private final MergingUpdateQueue myUpdateQueue =
      new MergingUpdateQueue("LocalChangesViewer", 300, true,
                             LocalChangesBrowser.this, LocalChangesBrowser.this);

    private void doUpdate() {
      myUpdateQueue.queue(new Update("update") {
        @Override
        public void run() {
          myViewer.rebuildTree();
        }
      });
    }

    @Override
    public void changeListsChanged() {
      doUpdate();
    }
  }
}