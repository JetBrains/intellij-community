/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.todo;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.checkin.TodoCheckinHandlerWorker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.psi.search.TodoPattern;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.util.*;

/**
 * @author irengrig
 */
public class CustomChangelistTodosTreeBuilder extends TodoTreeBuilder {
  public static final TodoItem[] EMPTY_ITEMS = new TodoItem[0];
  private final Project myProject;
  private final String myTitle;
  private final MultiMap<PsiFile, TodoItem> myMap;
  private final Set<PsiFile> myIncludedFiles;
  private PsiTodoSearchHelper myPsiTodoSearchHelper;
  private final ChangeListManager myChangeListManager;

  public CustomChangelistTodosTreeBuilder(JTree tree, DefaultTreeModel treeModel, Project project, final String title,
                                          final List<TodoItem> list) {
    super(tree, treeModel, project);
    myProject = project;
    myTitle = title;
    myMap = new MultiMap<>();
    myIncludedFiles = new HashSet<>();
    myChangeListManager = ChangeListManager.getInstance(myProject);
    initMap(list);
    initHelper();
  }

  private void initMap(List<TodoItem> list) {
    buildMap(list);
    myIncludedFiles.addAll(myMap.keySet());
  }

  private void buildMap(List<TodoItem> list) {
    for (TodoItem todoItem : list) {
      myMap.putValue(todoItem.getFile(), todoItem);
    }
  }

  private void initHelper() {
    myPsiTodoSearchHelper = new PsiTodoSearchHelper() {
      @NotNull
      @Override
      public PsiFile[] findFilesWithTodoItems() {
        final List<Change> changes = new ArrayList<>();
        final List<LocalChangeList> changeLists = myChangeListManager.getChangeLists();
        final Map<VirtualFile, Change> allChanges = new HashMap<>();
        for (LocalChangeList changeList : changeLists) {
          final Collection<Change> currChanges = changeList.getChanges();
          for (Change currChange : currChanges) {
            if (currChange.getAfterRevision() != null && currChange.getAfterRevision().getFile().getVirtualFile() != null) {
              allChanges.put(currChange.getAfterRevision().getFile().getVirtualFile(), currChange);
            }
          }
        }
        for (Iterator<PsiFile> iterator = myIncludedFiles.iterator(); iterator.hasNext(); ) {
          final PsiFile next = iterator.next();
          final Change change = allChanges.get(next.getVirtualFile());
          if (change != null) {
            changes.add(change);
          }
        }
        // a hack here with _todo filter
        final TodoCheckinHandlerWorker worker = new TodoCheckinHandlerWorker(myProject, changes, getTodoTreeStructure().getTodoFilter());
        worker.execute();
        buildMap(worker.inOneList());

        final Set<PsiFile> files = myMap.keySet();
        return files.toArray(new PsiFile[files.size()]);
      }

      @NotNull
      @Override
      public TodoItem[] findTodoItems(@NotNull PsiFile file) {
        return findPatternedTodoItems(file, getTodoTreeStructure().getTodoFilter());
      }

      @NotNull
      @Override
      public TodoItem[] findTodoItemsLight(@NotNull PsiFile file) {
        return findTodoItems(file);
      }

      @NotNull
      @Override
      public TodoItem[] findTodoItemsLight(@NotNull PsiFile file, int startOffset, int endOffset) {
        return findTodoItems(file, startOffset, endOffset);
      }

      @NotNull
      @Override
      public TodoItem[] findTodoItems(@NotNull PsiFile file, int startOffset, int endOffset) {
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
        return result.isEmpty() ? EMPTY_ITEMS : result.toArray(new TodoItem[result.size()]);
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
    };
  }

  private TodoItem[] findPatternedTodoItems(PsiFile file, final TodoFilter todoFilter) {
    if (! myIncludedFiles.contains(file)) return EMPTY_ITEMS;
    if (myDirtyFileSet.contains(file.getVirtualFile())) {
      myMap.remove(file);
      final Change change = myChangeListManager.getChange(file.getVirtualFile());
      if (change != null) {
        final TodoCheckinHandlerWorker
          worker = new TodoCheckinHandlerWorker(myProject, Collections.singletonList(change), todoFilter);
        worker.execute();
        final List<TodoItem> todoItems = worker.inOneList();
        if (todoItems != null && ! todoItems.isEmpty()) {
          for (TodoItem todoItem : todoItems) {
            myMap.putValue(file, todoItem);
          }
        }
      }
    }
    final Collection<TodoItem> todoItems = myMap.get(file);
    return todoItems == null || todoItems.isEmpty() ? EMPTY_ITEMS : todoItems.toArray(new TodoItem[todoItems.size()]);
  }

  @NotNull
  @Override
  protected TodoTreeStructure createTreeStructure() {
    return new CustomChangelistTodoTreeStructure(myProject, myPsiTodoSearchHelper);
  }

  @Override
  void rebuildCache() {
    myMap.clear();
    myFileTree.clear();
    myDirtyFileSet.clear();
    myFile2Highlighter.clear();

    TodoTreeStructure treeStructure=getTodoTreeStructure();
    PsiFile[] psiFiles= myPsiTodoSearchHelper.findFilesWithTodoItems();
    for(int i=0;i<psiFiles.length;i++){
      PsiFile psiFile=psiFiles[i];
      if(myPsiTodoSearchHelper.getTodoItemsCount(psiFile) > 0 && treeStructure.accept(psiFile)){
        myFileTree.add(psiFile.getVirtualFile());
      }
    }

    treeStructure.validateCache();
  }
}
