/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.checkin;

import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.TextDiffType;
import com.intellij.ide.todo.TodoFilter;
import com.intellij.ide.todo.TodoIndexPatternProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.search.LightIndexPatternSearch;
import com.intellij.psi.impl.search.TodoItemsCreator;
import com.intellij.psi.search.IndexPatternOccurrence;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.psi.search.searches.IndexPatternSearch;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.notNull;

/**
 * @author irengrig
 *         Date: 2/18/11
 *         Time: 5:16 PM
 */
public class TodoCheckinHandlerWorker {
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.checkin.TodoCheckinHandler");

  private final Project myProject;
  private final Collection<Change> myChanges;
  private final TodoFilter myTodoFilter;

  private final List<TodoItem> myAddedOrEditedTodos = new ArrayList<>();
  private final List<TodoItem> myInChangedTodos = new ArrayList<>();
  private final List<Pair<FilePath, String>> mySkipped = new SmartList<>();

  public TodoCheckinHandlerWorker(@NotNull Project project, @NotNull Collection<Change> changes, @Nullable TodoFilter todoFilter) {
    myProject = project;
    myChanges = changes;
    myTodoFilter = todoFilter;
  }

  public void execute() {
    for (Change change : myChanges) {
      ProgressManager.checkCanceled();
      if (change.getAfterRevision() == null) continue;
      FilePath afterFilePath = change.getAfterRevision().getFile();

      final VirtualFile afterFile = getFileWithRefresh(afterFilePath);
      if (afterFile == null || afterFile.isDirectory() || afterFile.getFileType().isBinary()) continue;

      List<TodoItem> newTodoItems = ReadAction.compute(() -> {
        if (!afterFile.isValid()) return null;
        PsiFile psiFile = PsiManager.getInstance(myProject).findFile(afterFile);
        if (psiFile == null) return null;

        PsiTodoSearchHelper searchHelper = PsiTodoSearchHelper.SERVICE.getInstance(myProject);
        return ContainerUtil.newArrayList(searchHelper.findTodoItems(psiFile));
      });
      if (newTodoItems == null) {
        mySkipped.add(Pair.create(afterFilePath, ourInvalidFile));
        continue;
      }

      applyFilterAndRemoveDuplicates(newTodoItems, myTodoFilter);

      if (change.getBeforeRevision() == null) {
        // take just all todos
        myAddedOrEditedTodos.addAll(newTodoItems);
        continue;
      }

      String beforeContent = getRevisionContent(change.getBeforeRevision());
      if (beforeContent == null) {
        mySkipped.add(Pair.create(afterFilePath, ourCannotLoadPreviousRevision));
        continue;
      }

      String afterContent = getRevisionContent(change.getAfterRevision());
      if (afterContent == null) {
        mySkipped.add(Pair.create(afterFilePath, ourCannotLoadCurrentRevision));
        continue;
      }

      try {
        new MyEditedFileProcessor(myProject, afterFilePath, beforeContent, afterContent, myTodoFilter)
          .process(newTodoItems);
      }
      catch (DiffTooBigException e) {
        LOG.info("File " + afterFilePath.getPath() + " is too big and there are too many changes to build a diff");
        mySkipped.add(Pair.create(afterFilePath, ourCannotLoadPreviousRevision));
      }
    }
  }

  @Nullable
  private static VirtualFile getFileWithRefresh(@NotNull FilePath filePath) {
    VirtualFile file = filePath.getVirtualFile();
    if (file == null) {
      file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(filePath.getIOFile());
    }
    return file;
  }

  private static void applyFilterAndRemoveDuplicates(final List<TodoItem> todoItems, final TodoFilter filter) {
    TodoItem previous = null;
    for (Iterator<TodoItem> iterator = todoItems.iterator(); iterator.hasNext(); ) {
      final TodoItem next = iterator.next();
      if (filter != null && !filter.contains(next.getPattern())) {
        iterator.remove();
        continue;
      }
      if (previous != null && next.getTextRange().equals(previous.getTextRange())) {
        iterator.remove();
        continue;
      }
      previous = next;
    }
  }

  private class MyEditedFileProcessor {
    @NotNull private final Project myProject;
    @NotNull private final String myBeforeContent;
    @NotNull private final String myAfterContent;
    private List<TodoItem> myOldItems;
    private LineFragment myCurrentLineFragment;
    private HashSet<String> myOldTodoTexts;
    @NotNull private final FilePath myAfterFile;
    @Nullable private final TodoFilter myTodoFilter;

    private MyEditedFileProcessor(@NotNull Project project,
                                  @NotNull FilePath afterFilePath,
                                  @NotNull String beforeContent,
                                  @NotNull String afterContent,
                                  @Nullable TodoFilter todoFilter) {
      myProject = project;
      myAfterFile = afterFilePath;
      myBeforeContent = beforeContent;
      myAfterContent = afterContent;
      myTodoFilter = todoFilter;
    }

    public void process(final List<TodoItem> newTodoItems) throws DiffTooBigException {
      List<LineFragment> lineFragments = getLineFragments(myBeforeContent, myAfterContent);
      lineFragments = ContainerUtil.filter(lineFragments, it -> DiffUtil.getLineDiffType(it) != TextDiffType.DELETED);

      StepIntersection.processIntersections(
        newTodoItems, lineFragments,
          TODO_ITEM_CONVERTOR, LINE_FRAGMENT_CONVERTOR,
        (todoItem, lineFragment) -> {
          ProgressManager.checkCanceled();
          if (myCurrentLineFragment == null || myCurrentLineFragment != lineFragment) {
            myCurrentLineFragment = lineFragment;
            myOldTodoTexts = null;
          }
          if (DiffUtil.getLineDiffType(lineFragment) == TextDiffType.INSERTED) {
            myAddedOrEditedTodos.add(todoItem);
          }
          else {
            // change
            checkEditedFragment(todoItem);
          }
        });
    }

    private void checkEditedFragment(TodoItem newTodoItem) {
      if (myOldItems == null) {
        PsiFile beforePsiFile = ReadAction.compute(() -> {
          return PsiFileFactory.getInstance(myProject)
            .createFileFromText("old" + myAfterFile.getName(), myAfterFile.getFileType(), myBeforeContent);
        });

        final Collection<IndexPatternOccurrence> all =
          LightIndexPatternSearch.SEARCH.createQuery(new IndexPatternSearch.SearchParameters(beforePsiFile, TodoIndexPatternProvider
            .getInstance())).findAll();

        final TodoItemsCreator todoItemsCreator = new TodoItemsCreator();
        myOldItems = new ArrayList<>();
        if (all.isEmpty()) {
          myAddedOrEditedTodos.add(newTodoItem);
          return;
        }
        for (IndexPatternOccurrence occurrence : all) {
          myOldItems.add(todoItemsCreator.createTodo(occurrence));
        }
        applyFilterAndRemoveDuplicates(myOldItems, myTodoFilter);
      }
      if (myOldTodoTexts == null) {
        myOldTodoTexts = new HashSet<>();
        StepIntersection.processIntersections(
          Collections.singletonList(myCurrentLineFragment), myOldItems,
          LINE_FRAGMENT_CONVERTOR, TODO_ITEM_CONVERTOR,
          (lineFragment, todoItem) -> myOldTodoTexts.add(getTodoText(todoItem, myBeforeContent)));
      }
      final String text = getTodoText(newTodoItem, myAfterContent);
      if (! myOldTodoTexts.contains(text)) {
        myAddedOrEditedTodos.add(newTodoItem);
      } else {
        myInChangedTodos.add(newTodoItem);
      }
    }
  }

  public List<TodoItem> getAddedOrEditedTodos() {
    return myAddedOrEditedTodos;
  }

  public List<TodoItem> getInChangedTodos() {
    return myInChangedTodos;
  }

  public List<Pair<FilePath, String>> getSkipped() {
    return mySkipped;
  }

  private static String getTodoText(TodoItem oldItem, final String content) {
    final String fragment = content.substring(oldItem.getTextRange().getStartOffset(), oldItem.getTextRange().getEndOffset());
    return StringUtil.join(fragment.split("\\s"), " ");
  }

  private static List<LineFragment> getLineFragments(@NotNull String beforeContent, @NotNull String afterContent)
    throws DiffTooBigException {
    ProgressIndicator indicator = notNull(ProgressManager.getInstance().getProgressIndicator(), DumbProgressIndicator.INSTANCE);
    return ComparisonManager.getInstance().compareLines(beforeContent, afterContent, ComparisonPolicy.IGNORE_WHITESPACES, indicator);
  }

  @Nullable
  private static String getRevisionContent(@NotNull ContentRevision revision) {
    try {
      return revision.getContent();
    }
    catch (VcsException e) {
      LOG.info(e);
      return null;
    }
  }

  private final static String ourInvalidFile = "Invalid file (s)";
  private final static String ourCannotLoadPreviousRevision = "Can not load previous revision";
  private final static String ourCannotLoadCurrentRevision = "Can not load current revision";

  private static final Convertor<TodoItem, TextRange> TODO_ITEM_CONVERTOR = o -> {
    final TextRange textRange = o.getTextRange();
    return new TextRange(textRange.getStartOffset(), textRange.getEndOffset() - 1);
  };

  private static final Convertor<LineFragment, TextRange> LINE_FRAGMENT_CONVERTOR = o -> {
    int start = o.getStartOffset2();
    int end = o.getEndOffset2();
    return new TextRange(start, Math.max(start, end - 1));
  };

  public List<TodoItem> inOneList() {
    final List<TodoItem> list = new ArrayList<>();
    list.addAll(getAddedOrEditedTodos());
    list.addAll(getInChangedTodos());
    return list;
  }
}
