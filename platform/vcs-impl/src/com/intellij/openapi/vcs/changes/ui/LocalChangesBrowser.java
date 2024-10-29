// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.DisposableUpdate;
import com.intellij.util.ui.update.MergingUpdateQueue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ApiStatus.Internal
public abstract class LocalChangesBrowser extends AsyncChangesBrowserBase implements Disposable {
  @NotNull private final ToggleChangeDiffAction myToggleChangeDiffAction;

  public LocalChangesBrowser(@NotNull Project project) {
    super(project, true, true);

    myToggleChangeDiffAction = new ToggleChangeDiffAction();

    ChangeListManager.getInstance(myProject).addChangeListListener(new MyChangeListListener(), this);
    init();

    myViewer.setInclusionModel(new DefaultInclusionModel(ChangeListChange.HASHING_STRATEGY));
  }

  @Override
  public void dispose() {
    shutdown();
  }

  @NotNull
  @Override
  protected List<AnAction> createDiffActions() {
    return ContainerUtil.append(
      super.createDiffActions(),
      myToggleChangeDiffAction
    );
  }

  public void setIncludedChangesBy(@NotNull Collection<? extends Change> changes) {
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


  public void setToggleActionTitle(@NlsActions.ActionText @Nullable String title) {
    myToggleChangeDiffAction.getTemplatePresentation().setText(title);
  }


  private class ToggleChangeDiffAction extends CheckboxAction implements DumbAware {
    ToggleChangeDiffAction() {
      super(VcsBundle.message("checkbox.include"));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      Change change = e.getData(VcsDataKeys.CURRENT_CHANGE);
      if (change == null) return false;
      return myViewer.isIncluded(change);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      Change change = e.getData(VcsDataKeys.CURRENT_CHANGE);
      if (change == null) return;

      if (state) {
        myViewer.includeChange(change);
        myViewer.logInclusionToggleEvents(false, e);
      }
      else {
        myViewer.excludeChange(change);
        myViewer.logInclusionToggleEvents(true, e);
      }
    }
  }

  private class MyChangeListListener extends ChangeListAdapter {
    @NotNull private final MergingUpdateQueue myUpdateQueue =
      new MergingUpdateQueue("LocalChangesViewer", 300, true,
                             LocalChangesBrowser.this, LocalChangesBrowser.this);

    private void doUpdate() {
      myUpdateQueue.queue(DisposableUpdate.createDisposable(myUpdateQueue, "update", () -> {
        myViewer.rebuildTree();
      }));
    }

    @Override
    public void changeListsChanged() {
      doUpdate();
    }
  }

  public static class NonEmptyChangeLists extends LocalChangesBrowser {
    public NonEmptyChangeLists(@NotNull Project project) {
      super(project);
      myViewer.rebuildTree();
    }

    @NotNull
    @Override
    protected AsyncChangesTreeModel getChangesTreeModel() {
      return SimpleAsyncChangesTreeModel.create(grouping -> {
        List<LocalChangeList> allLists = ChangeListManager.getInstance(myProject).getChangeLists();
        List<LocalChangeList> selectedLists = ContainerUtil.filter(allLists, list -> !list.getChanges().isEmpty());
        return TreeModelBuilder.buildFromChangeLists(myProject, grouping, selectedLists,
                                                     Registry.is("vcs.skip.single.default.changelist"));
      });
    }
  }

  public static class SelectedChangeLists extends LocalChangesBrowser {
    @NotNull private final Set<String> myChangeListNames;

    public SelectedChangeLists(@NotNull Project project, @NotNull Collection<? extends LocalChangeList> changeLists) {
      super(project);
      myChangeListNames = ContainerUtil.map2Set(changeLists, LocalChangeList::getName);
      myViewer.rebuildTree();
    }

    @NotNull
    @Override
    protected AsyncChangesTreeModel getChangesTreeModel() {
      return SimpleAsyncChangesTreeModel.create(grouping -> {
        List<LocalChangeList> allLists = ChangeListManager.getInstance(myProject).getChangeLists();
        List<LocalChangeList> selectedLists = ContainerUtil.filter(allLists, list -> myChangeListNames.contains(list.getName()));
        return TreeModelBuilder.buildFromChangeLists(myProject, grouping, selectedLists,
                                                     Registry.is("vcs.skip.single.default.changelist"));
      });
    }
  }

  public static class AllChanges extends LocalChangesBrowser {
    public AllChanges(@NotNull Project project) {
      super(project);
      myViewer.rebuildTree();
    }

    @NotNull
    @Override
    protected AsyncChangesTreeModel getChangesTreeModel() {
      return SimpleAsyncChangesTreeModel.create(grouping -> {
        Collection<Change> allChanges = ChangeListManager.getInstance(myProject).getAllChanges();
        return TreeModelBuilder.buildFromChanges(myProject, grouping, allChanges, null);
      });
    }
  }
}