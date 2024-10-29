// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.psi.search.TodoPattern;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public abstract class CustomChangelistTodosTreeBuilder extends TodoTreeBuilder {
  public static final TodoItem[] EMPTY_ITEMS = TodoItem.EMPTY_ARRAY;

  private final PsiTodoSearchHelper myCustomPsiTodoSearchHelper;

  public CustomChangelistTodosTreeBuilder(@NotNull JTree tree,
                                          @NotNull Project project,
                                          @NotNull Collection<? extends TodoItem> initialTodoItems) {
    super(tree, project);

    myCustomPsiTodoSearchHelper = new MyPsiTodoSearchHelper(initialTodoItems);
  }

  @Override
  protected @NotNull PsiTodoSearchHelper getSearchHelper() {
    return myCustomPsiTodoSearchHelper;
  }

  @NotNull
  protected abstract Set<TodoItem> doFindAllTodoItems(@Nullable TodoFilter todoFilter);

  @NotNull
  protected abstract Set<TodoItem> doFindTodoForFile(@NotNull PsiFile file, @Nullable TodoFilter todoFilter);

  private boolean isFileDirty(@NotNull PsiFile file) {
    return myDirtyFileSet.contains(file.getVirtualFile());
  }

  private class MyPsiTodoSearchHelper implements PsiTodoSearchHelper {
    private final MultiMap<PsiFile, TodoItem> myMap = new MultiMap<>();

    private MyPsiTodoSearchHelper(@NotNull Collection<? extends TodoItem> todoItems) {
      buildMap(todoItems);
    }

    private void buildMap(@NotNull Collection<? extends TodoItem> todoItems) {
      synchronized (myMap) {
        myMap.clear();
        for (TodoItem todoItem : todoItems) {
          myMap.putValue(todoItem.getFile(), todoItem);
        }
      }
    }

    @Override
    public PsiFile @NotNull [] findFilesWithTodoItems() {
      // a hack here with _todo filter
      TodoFilter todoFilter = getTodoTreeStructure().getTodoFilter();

      Set<TodoItem> todoItems = doFindAllTodoItems(todoFilter);
      buildMap(todoItems);

      synchronized (myMap) {
        final Set<PsiFile> files = myMap.keySet();
        return files.toArray(PsiFile.EMPTY_ARRAY);
      }
    }

    @Override
    public boolean processFilesWithTodoItems(@NotNull Processor<? super PsiFile> processor) {
      return ContainerUtil.process(findFilesWithTodoItems(), processor);
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
      return result.isEmpty() ? EMPTY_ITEMS : result.toArray(TodoItem.EMPTY_ARRAY);
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
      if (isFileDirty(file)) {
        Set<TodoItem> todoItems = doFindTodoForFile(file, todoFilter);
        synchronized (myMap) {
          myMap.remove(file);
          for (TodoItem todoItem : todoItems) {
            myMap.putValue(file, todoItem);
          }
        }
      }

      synchronized (myMap) {
        final Collection<TodoItem> todoItems = myMap.get(file);
        return todoItems.isEmpty() ? EMPTY_ITEMS : todoItems.toArray(TodoItem.EMPTY_ARRAY);
      }
    }
  }

  @Override
  protected @NotNull TodoTreeStructure createTreeStructure() {
    return new CustomChangelistTodoTreeStructure(myProject, myCustomPsiTodoSearchHelper);
  }
}
