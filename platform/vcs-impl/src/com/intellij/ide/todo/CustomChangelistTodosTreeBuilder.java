// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.todo;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.TodoCheckinHandlerWorker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.psi.search.TodoPattern;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author irengrig
 */
public class CustomChangelistTodosTreeBuilder extends TodoTreeBuilder {
  public static final TodoItem[] EMPTY_ITEMS = new TodoItem[0];

  private final PsiTodoSearchHelper myCustomPsiTodoSearchHelper;

  private final Set<PsiFile> myIncludedFiles;
  private final @Nullable Set<String> myIncludedChangeListsIds;

  public CustomChangelistTodosTreeBuilder(@NotNull JTree tree,
                                          @NotNull Project project,
                                          @NotNull Collection<Change> changes,
                                          @NotNull Collection<? extends TodoItem> todoItems) {
    super(tree, project);

    myIncludedFiles = collectIncludedFiles(todoItems);
    myIncludedChangeListsIds = collectIncludedChangeListsIds(project, changes);

    myCustomPsiTodoSearchHelper = new MyPsiTodoSearchHelper(todoItems);
  }

  @Override
  protected @NotNull PsiTodoSearchHelper getSearchHelper() {
    return myCustomPsiTodoSearchHelper;
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

  private class MyPsiTodoSearchHelper implements PsiTodoSearchHelper {
    private final MultiMap<PsiFile, TodoItem> myMap = new MultiMap<>();

    private MyPsiTodoSearchHelper(@NotNull Collection<? extends TodoItem> todoItems) {
      buildMap(todoItems);
    }

    @NotNull
    private Set<TodoItem> doFindAllTodoItems(TodoFilter todoFilter) {
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
    private Set<TodoItem> doFindTodoForFile(@NotNull PsiFile file, @Nullable TodoFilter todoFilter) {
      if (!myIncludedFiles.contains(file)) return Collections.emptySet();

      Change change = ChangeListManager.getInstance(myProject).getChange(file.getVirtualFile());
      if (change == null) return Collections.emptySet();

      List<Change> changes = Collections.singletonList(change);
      TodoCheckinHandlerWorker worker = new TodoCheckinHandlerWorker(myProject, changes, todoFilter);
      worker.execute();

      return worker.inOneList();
    }

    private void buildMap(@NotNull Collection<? extends TodoItem> todoItems) {
      myMap.clear();
      for (TodoItem todoItem : todoItems) {
        myMap.putValue(todoItem.getFile(), todoItem);
      }
    }

    @Override
    public PsiFile @NotNull [] findFilesWithTodoItems() {
      // a hack here with _todo filter
      TodoFilter todoFilter = getTodoTreeStructure().getTodoFilter();

      Set<TodoItem> todoItems = doFindAllTodoItems(todoFilter);
      buildMap(todoItems);

      final Set<PsiFile> files = myMap.keySet();
      return files.toArray(PsiFile.EMPTY_ARRAY);
    }

    @Override
    public boolean processFilesWithTodoItems(@NotNull Processor<? super PsiFile> processor) {
      return ContainerUtil.process(findFilesWithTodoItems(), processor);
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

    @Override
    public TodoItem @NotNull [] findTodoItems(@NotNull PsiFile file) {
      return findPatternedTodoItems(file, getTodoTreeStructure().getTodoFilter());
    }

    @Override
    public TodoItem @NotNull [] findTodoItemsLight(@NotNull PsiFile file) {
      return findTodoItems(file);
    }

    @Override
    public TodoItem @NotNull [] findTodoItemsLight(@NotNull PsiFile file, int startOffset, int endOffset) {
      return findTodoItems(file, startOffset, endOffset);
    }

    @Override
    public TodoItem @NotNull [] findTodoItems(@NotNull PsiFile file, int startOffset, int endOffset) {
      final TodoItem[] todoItems = findTodoItems(file);
      if (todoItems.length == 0) {
        return todoItems;
      }
      final TextRange textRange = new TextRange(startOffset, endOffset);
      final List<TodoItem> result = new ArrayList<>();
      for (TodoItem todoItem : todoItems) {
        if (todoItem.getTextRange().contains(textRange)) {
          result.add(todoItem);
        }
      }
      return result.isEmpty() ? EMPTY_ITEMS : result.toArray(new TodoItem[0]);
    }

    @Override
    public int getTodoItemsCount(@NotNull PsiFile file) {
      return findTodoItems(file).length;
    }

    @Override
    public int getTodoItemsCount(@NotNull PsiFile file, @NotNull TodoPattern pattern) {
      final TodoFilter filter = new TodoFilter();
      filter.addTodoPattern(pattern);
      return findPatternedTodoItems(file, filter).length;
    }

    private TodoItem[] findPatternedTodoItems(PsiFile file, final TodoFilter todoFilter) {
      if (myDirtyFileSet.contains(file.getVirtualFile())) {
        Set<TodoItem> todoItems = doFindTodoForFile(file, todoFilter);
        myMap.remove(file);
        for (TodoItem todoItem : todoItems) {
          myMap.putValue(file, todoItem);
        }
      }

      final Collection<TodoItem> todoItems = myMap.get(file);
      return todoItems.isEmpty() ? EMPTY_ITEMS : todoItems.toArray(new TodoItem[0]);
    }
  }

  @Override
  protected @NotNull TodoTreeStructure createTreeStructure() {
    return new CustomChangelistTodoTreeStructure(myProject, myCustomPsiTodoSearchHelper);
  }
}
