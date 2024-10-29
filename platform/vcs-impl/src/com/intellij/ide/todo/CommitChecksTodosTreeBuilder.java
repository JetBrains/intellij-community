// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.TodoCheckinHandlerWorker;
import com.intellij.openapi.vcs.impl.PartialChangesUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.TodoItem;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

@ApiStatus.Internal
public final class CommitChecksTodosTreeBuilder extends CustomChangelistTodosTreeBuilder {
  private static final Logger LOG = Logger.getInstance(CommitChecksTodosTreeBuilder.class);

  private final Set<VirtualFile> myIncludedFiles;
  private final @Nullable Set<String> myIncludedChangeListsIds;

  public CommitChecksTodosTreeBuilder(@NotNull JTree tree,
                                      @NotNull Project project,
                                      @NotNull Collection<Change> changes,
                                      @NotNull Collection<? extends TodoItem> todoItems) {
    super(tree, project, todoItems);

    myIncludedFiles = collectIncludedFiles(todoItems);
    myIncludedChangeListsIds = collectIncludedChangeListsIds(project, changes);

    if (myIncludedChangeListsIds != null && myIncludedChangeListsIds.size() > 1) {
      LOG.warn("Processing TODO for multiple changelists might be incorrect" + myIncludedChangeListsIds);
    }
  }

  private static @NotNull Set<VirtualFile> collectIncludedFiles(@NotNull Collection<? extends TodoItem> todoItems) {
    HashSet<VirtualFile> files = new HashSet<>();
    for (TodoItem item : todoItems) {
      PsiFile psiFile = item.getFile();
      files.add(psiFile.getVirtualFile());
    }
    return files;
  }

  private static @Nullable Set<String> collectIncludedChangeListsIds(@NotNull Project project, @NotNull Collection<? extends Change> changes) {
    if (!ChangeListManager.getInstance(project).areChangeListsEnabled()) return null;

    HashSet<String> ids = new HashSet<>();
    for (Change change : changes) {
      if (change instanceof ChangeListChange changeListChange) {
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
    List<Change> allChanges = new ArrayList<>();
    if (myIncludedChangeListsIds == null) {
      allChanges.addAll(ChangeListManager.getInstance(myProject).getAllChanges());
    }
    else {
      for (String changeListId : myIncludedChangeListsIds) {
        LocalChangeList changeList = ChangeListManager.getInstance(myProject).getChangeList(changeListId);
        if (changeList != null) {
          allChanges.addAll(changeList.getChanges());
        }
      }
    }

    List<Change> changes = new ArrayList<>();
    for (Change change : allChanges) {
      FilePath afterPath = ChangesUtil.getAfterPath(change);
      VirtualFile changeFile = afterPath != null ? afterPath.getVirtualFile() : null;
      if (changeFile != null && myIncludedFiles.contains(changeFile)) {
        changes.add(change);
      }
    }

    TodoCheckinHandlerWorker worker = new TodoCheckinHandlerWorker(myProject,
                                                                   PartialChangesUtil.wrapPartialChanges(myProject, changes),
                                                                   todoFilter);
    worker.execute();

    return worker.inOneList();
  }

  @NotNull
  @Override
  protected Set<TodoItem> doFindTodoForFile(@NotNull PsiFile psiFile, @Nullable TodoFilter todoFilter) {
    VirtualFile file = psiFile.getVirtualFile();
    if (!myIncludedFiles.contains(file)) return Collections.emptySet();

    Change change = ChangeListManager.getInstance(myProject).getChange(file);
    if (change == null) return Collections.emptySet();

    List<Change> changes = Collections.singletonList(change);
    TodoCheckinHandlerWorker worker = new TodoCheckinHandlerWorker(myProject,
                                                                   PartialChangesUtil.wrapPartialChanges(myProject, changes),
                                                                   todoFilter);
    worker.execute();

    return worker.inOneList();
  }
}
