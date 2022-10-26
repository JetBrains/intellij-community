// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.todo;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.TodoCheckinHandlerWorker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.TodoItem;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class CommitChecksTodosTreeBuilder extends CustomChangelistTodosTreeBuilder {
  private final Set<PsiFile> myIncludedFiles;
  private final @Nullable Set<String> myIncludedChangeListsIds;

  public CommitChecksTodosTreeBuilder(@NotNull JTree tree,
                                      @NotNull Project project,
                                      @NotNull Collection<Change> changes,
                                      @NotNull Collection<? extends TodoItem> todoItems) {
    super(tree, project, todoItems);

    myIncludedFiles = collectIncludedFiles(todoItems);
    myIncludedChangeListsIds = collectIncludedChangeListsIds(project, changes);
  }

  private static @NotNull Set<PsiFile> collectIncludedFiles(@NotNull Collection<? extends TodoItem> todoItems) {
    HashSet<PsiFile> files = new HashSet<>();
    for (TodoItem item : todoItems) {
      files.add(item.getFile());
    }
    return files;
  }

  private static @Nullable Set<String> collectIncludedChangeListsIds(@NotNull Project project, @NotNull Collection<Change> changes) {
    if (!ChangeListManager.getInstance(project).areChangeListsEnabled()) return null;

    HashSet<String> ids = new HashSet<>();
    for (Change change : changes) {
      if (change instanceof ChangeListChange) {
        ChangeListChange changeListChange = (ChangeListChange)change;
        ids.add(changeListChange.getChangeListId());
      }
      else {
        return null; // Show all changelists
      }
    }
    return ids;
  }

  @NotNull
  @Override
  protected Set<TodoItem> doFindAllTodoItems(@Nullable TodoFilter todoFilter) {
    MultiMap<VirtualFile, Change> allChanges = new MultiMap<>();
    if (myIncludedChangeListsIds == null) {
      putChangesForLocalFiles(allChanges, ChangeListManager.getInstance(myProject).getAllChanges());
    }
    else {
      for (String changeListId : myIncludedChangeListsIds) {
        LocalChangeList changeList = ChangeListManager.getInstance(myProject).getChangeList(changeListId);
        if (changeList != null) {
          putChangesForLocalFiles(allChanges, changeList.getChanges());
        }
      }
    }

    List<Change> changes = new ArrayList<>();
    for (PsiFile next : myIncludedFiles) {
      changes.addAll(allChanges.get(next.getVirtualFile()));
    }

    TodoCheckinHandlerWorker worker = new TodoCheckinHandlerWorker(myProject, changes, todoFilter);
    worker.execute();

    return worker.inOneList();
  }

  @NotNull
  @Override
  protected Set<TodoItem> doFindTodoForFile(@NotNull PsiFile file, @Nullable TodoFilter todoFilter) {
    if (!myIncludedFiles.contains(file)) return Collections.emptySet();

    Change change = ChangeListManager.getInstance(myProject).getChange(file.getVirtualFile());
    if (change == null) return Collections.emptySet();

    List<Change> changes = Collections.singletonList(change);
    TodoCheckinHandlerWorker worker = new TodoCheckinHandlerWorker(myProject, changes, todoFilter);
    worker.execute();

    return worker.inOneList();
  }

  private static void putChangesForLocalFiles(@NotNull MultiMap<VirtualFile, Change> changesMap, @NotNull Collection<Change> changes) {
    for (Change change : changes) {
      FilePath afterPath = ChangesUtil.getAfterPath(change);
      VirtualFile file = afterPath != null ? afterPath.getVirtualFile() : null;
      if (file != null) {
        changesMap.putValue(file, change);
      }
    }
  }
}
