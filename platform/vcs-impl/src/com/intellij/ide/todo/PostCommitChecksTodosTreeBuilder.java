// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.checkin.TodoCheckinHandlerWorker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.TodoItem;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

@ApiStatus.Internal
public final class PostCommitChecksTodosTreeBuilder extends CustomChangelistTodosTreeBuilder {
  private final Map<VirtualFile, Change> myChanges = new HashMap<>();

  public PostCommitChecksTodosTreeBuilder(@NotNull JTree tree,
                                          @NotNull Project project,
                                          @NotNull Collection<? extends Change> changes,
                                          @NotNull Collection<? extends TodoItem> todoItems) {
    super(tree, project, todoItems);

    for (Change change : changes) {
      FilePath afterPath = ChangesUtil.getAfterPath(change);
      VirtualFile changeFile = afterPath != null ? afterPath.getVirtualFile() : null;
      if (changeFile != null) {
        myChanges.put(changeFile, change);
      }
    }
  }

  @NotNull
  @Override
  protected Set<TodoItem> doFindAllTodoItems(@Nullable TodoFilter todoFilter) {
    Collection<Change> changes = myChanges.values();
    TodoCheckinHandlerWorker worker = new TodoCheckinHandlerWorker(myProject, changes, todoFilter);
    worker.execute();

    return worker.inOneList();
  }

  @NotNull
  @Override
  protected Set<TodoItem> doFindTodoForFile(@NotNull PsiFile psiFile, @Nullable TodoFilter todoFilter) {
    VirtualFile file = psiFile.getVirtualFile();
    Change change = myChanges.get(file);
    if (change == null) return Collections.emptySet();

    List<Change> changes = Collections.singletonList(change);
    TodoCheckinHandlerWorker worker = new TodoCheckinHandlerWorker(myProject, changes, todoFilter);
    worker.execute();

    return worker.inOneList();
  }
}
