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

  private final PsiTodoSearchHelper myPsiTodoSearchHelper = new MyPsiTodoSearchHelper();
  private final MultiMap<PsiFile, TodoItem> myMap = new MultiMap<>();

  private final Set<PsiFile> myIncludedFiles;
  @Nullable private final Set<String> myIncludedChangeListsIds;

  public CustomChangelistTodosTreeBuilder(@NotNull JTree tree,
                                          @NotNull Project project,
                                          @NotNull Collection<Change> changes,
                                          @NotNull Collection<? extends TodoItem> todoItems) {
    super(tree, project);

    myIncludedFiles = collectIncludedFiles(todoItems);
    myIncludedChangeListsIds = collectIncludedChangeListsIds(project, changes);

    buildMap(todoItems);
  }

  @Override
  protected @NotNull PsiTodoSearchHelper getSearchHelper() {
    return myPsiTodoSearchHelper;
  }

  @NotNull
  private static Set<PsiFile> collectIncludedFiles(@NotNull Collection<? extends TodoItem> todoItems) {
    HashSet<PsiFile> files = new HashSet<>();
    for (TodoItem item : todoItems) {
      files.add(item.getFile());
    }
    return files;
  }

  @Nullable
  private static Set<String> collectIncludedChangeListsIds(@NotNull Project project, @NotNull Collection<Change> changes) {
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

  private void buildMap(Collection<? extends TodoItem> todoItems) {
    myMap.clear();
    for (TodoItem todoItem : todoItems) {
      myMap.putValue(todoItem.getFile(), todoItem);
    }
  }

  private class MyPsiTodoSearchHelper implements PsiTodoSearchHelper {
    @Override
    public PsiFile @NotNull [] findFilesWithTodoItems() {
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

      // a hack here with _todo filter
      final TodoCheckinHandlerWorker worker = new TodoCheckinHandlerWorker(myProject, changes, getTodoTreeStructure().getTodoFilter());
      worker.execute();
      buildMap(worker.inOneList());

      final Set<PsiFile> files = myMap.keySet();
      return files.toArray(PsiFile.EMPTY_ARRAY);
    }

    @Override
    public boolean processFilesWithTodoItems(@NotNull Processor<? super PsiFile> processor) {
      return ContainerUtil.process(findFilesWithTodoItems(), processor);
    }

    private void putChangesForLocalFiles(@NotNull MultiMap<VirtualFile, Change> changesMap, @NotNull Collection<Change> changes) {
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
  }

  private TodoItem[] findPatternedTodoItems(PsiFile file, final TodoFilter todoFilter) {
    if (! myIncludedFiles.contains(file)) return EMPTY_ITEMS;
    if (myDirtyFileSet.contains(file.getVirtualFile())) {
      myMap.remove(file);
      final Change change = ChangeListManager.getInstance(myProject).getChange(file.getVirtualFile());
      if (change != null) {
        final TodoCheckinHandlerWorker
          worker = new TodoCheckinHandlerWorker(myProject, Collections.singletonList(change), todoFilter);
        worker.execute();
        final Collection<TodoItem> todoItems = worker.inOneList();
        if (todoItems != null && ! todoItems.isEmpty()) {
          for (TodoItem todoItem : todoItems) {
            myMap.putValue(file, todoItem);
          }
        }
      }
    }
    final Collection<TodoItem> todoItems = myMap.get(file);
    return todoItems.isEmpty() ? EMPTY_ITEMS : todoItems.toArray(new TodoItem[0]);
  }

  @NotNull
  @Override
  protected TodoTreeStructure createTreeStructure() {
    return new CustomChangelistTodoTreeStructure(myProject, myPsiTodoSearchHelper);
  }

}
