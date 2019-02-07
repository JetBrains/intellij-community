// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin;

import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.ide.todo.TodoFilter;
import com.intellij.ide.todo.TodoIndexPatternProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
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
 */
public class TodoCheckinHandlerWorker {
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.checkin.TodoCheckinHandler");

  private final Project myProject;
  private final Collection<? extends Change> myChanges;
  private final TodoFilter myTodoFilter;

  private final Set<TodoItem> myAddedOrEditedTodos = new HashSet<>();
  private final Set<TodoItem> myInChangedTodos = new HashSet<>();
  private final List<Pair<FilePath, String>> mySkipped = new SmartList<>();

  public TodoCheckinHandlerWorker(@NotNull Project project, @NotNull Collection<? extends Change> changes, @Nullable TodoFilter todoFilter) {
    myProject = project;
    myChanges = changes;
    myTodoFilter = todoFilter;
  }

  public void execute() {
    for (Change change : myChanges) {
      ProgressManager.checkCanceled();
      if (change.getAfterRevision() == null) continue;
      FilePath afterFilePath = change.getAfterRevision().getFile();

      MyEditedFileProcessor fileProcessor = ReadAction.compute(() -> {
        final VirtualFile afterFile = getFileWithRefresh(afterFilePath);
        if (afterFile == null || afterFile.isDirectory() || afterFile.getFileType().isBinary()) {
          return null; // skip detection
        }

        PsiFile afterPsiFile = afterFile.isValid() ? PsiManager.getInstance(myProject).findFile(afterFile) : null;
        if (afterPsiFile == null) {
          mySkipped.add(Pair.create(afterFilePath, ourInvalidFile));
          return null;
        }

        PsiTodoSearchHelper searchHelper = PsiTodoSearchHelper.SERVICE.getInstance(myProject);
        List<TodoItem> newTodoItems = ContainerUtil.newArrayList(searchHelper.findTodoItems(afterPsiFile));
        applyFilterAndRemoveDuplicates(newTodoItems, myTodoFilter);

        if (change.getBeforeRevision() == null) {
          // take just all todos
          myAddedOrEditedTodos.addAll(newTodoItems);
          return null;
        }

        String rawBeforeContent = getRevisionContent(change.getBeforeRevision());
        if (rawBeforeContent == null) {
          mySkipped.add(Pair.create(afterFilePath, ourCannotLoadPreviousRevision));
          return null;
        }

        Document afterDocument = FileDocumentManager.getInstance().getDocument(afterFile);
        if (afterDocument == null) {
          mySkipped.add(Pair.create(afterFilePath, ourCannotLoadCurrentRevision));
          return null;
        }

        String beforeContent = StringUtil.convertLineSeparators(rawBeforeContent);
        String afterContent = afterDocument.getText();

        return new MyEditedFileProcessor(myProject, afterFilePath, beforeContent, afterContent, newTodoItems, myTodoFilter);
      });

      try {
        if (fileProcessor != null) fileProcessor.process();
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
    @NotNull private final FilePath myAfterFile;
    @NotNull private final List<? extends TodoItem> myNewTodoItems;
    private final TodoFilter myTodoFilter;

    private MyEditedFileProcessor(@NotNull Project project,
                                  @NotNull FilePath afterFilePath,
                                  @NotNull String beforeContent,
                                  @NotNull String afterContent,
                                  @NotNull List<? extends TodoItem> newTodoItems,
                                  @Nullable TodoFilter todoFilter) {
      myProject = project;
      myAfterFile = afterFilePath;
      myBeforeContent = beforeContent;
      myAfterContent = afterContent;
      myNewTodoItems = newTodoItems;
      myTodoFilter = todoFilter;
    }

    public void process() throws DiffTooBigException {
      List<LineFragment> lineFragments = getLineFragments(myBeforeContent, myAfterContent);

      List<Pair<TodoItem, LineFragment>> changedTodoItems = new ArrayList<>();
      StepIntersection.processIntersections(
        myNewTodoItems, lineFragments,
        TODO_ITEM_CONVERTOR, new RightLineFragmentConvertor(myAfterContent),
        (todoItem, lineFragment) -> changedTodoItems.add(Pair.create(todoItem, lineFragment)));

      if (changedTodoItems.isEmpty()) return;

      final PsiFile beforePsiFile = ReadAction.compute(() -> PsiFileFactory.getInstance(myProject)
                                                                           .createFileFromText("old" + myAfterFile.getName(),
                                                                                               myAfterFile.getFileType(), myBeforeContent));

      final IndexPatternSearch.SearchParameters searchParameters =
        new IndexPatternSearch.SearchParameters(beforePsiFile, TodoIndexPatternProvider.getInstance(),
                                                TodoConfiguration.getInstance().isMultiLine());
      final Collection<IndexPatternOccurrence> patternOccurrences = LightIndexPatternSearch.SEARCH.createQuery(searchParameters).findAll();

      if (patternOccurrences.isEmpty()) {
        for (Pair<TodoItem, LineFragment> pair : changedTodoItems) {
          myAddedOrEditedTodos.add(pair.first);
        }
        return;
      }

      final List<TodoItem> oldTodoItems = new ArrayList<>();
      final TodoItemsCreator todoItemsCreator = new TodoItemsCreator();
      for (IndexPatternOccurrence occurrence : patternOccurrences) {
        oldTodoItems.add(todoItemsCreator.createTodo(occurrence));
      }
      applyFilterAndRemoveDuplicates(oldTodoItems, myTodoFilter);


      LineFragment lastLineFragment = null;
      HashSet<String> oldTodoTexts = new HashSet<>();
      for (Pair<TodoItem, LineFragment> pair : changedTodoItems) {
        TodoItem todoItem = pair.first;
        LineFragment lineFragment = pair.second;

        if (lineFragment != lastLineFragment) {
          oldTodoTexts.clear();
          StepIntersection.processElementIntersections(
            lineFragment, oldTodoItems,
            new LeftLineFragmentConvertor(myBeforeContent), TODO_ITEM_CONVERTOR,
            (fragment, oldTodoItem) -> oldTodoTexts.add(getTodoText(oldTodoItem, myBeforeContent)));
          lastLineFragment = lineFragment;
        }

        final String text = getTodoText(todoItem, myAfterContent);
        if (!oldTodoTexts.contains(text)) {
          myAddedOrEditedTodos.add(todoItem);
        }
        else if (TODO_ITEM_CONVERTOR.convert(todoItem).getEndOffset() >= lineFragment.getStartOffset2()) {
          myInChangedTodos.add(todoItem);
        }
      }
      myInChangedTodos.removeAll(myAddedOrEditedTodos);
    }
  }

  public Set<TodoItem> getAddedOrEditedTodos() {
    return myAddedOrEditedTodos;
  }

  public Set<TodoItem> getInChangedTodos() {
    return myInChangedTodos;
  }

  public List<Pair<FilePath, String>> getSkipped() {
    return mySkipped;
  }

  private static String getTodoText(TodoItem item, final String content) {
    StringJoiner joiner = new StringJoiner("\n");
    joiner.add(getTodoPartText(content, item.getTextRange()));
    item.getAdditionalTextRanges().forEach(r -> joiner.add(getTodoPartText(content, r)));
    return joiner.toString();
  }

  private static String getTodoPartText(String content, TextRange textRange) {
    final String fragment = content.substring(textRange.getStartOffset(), textRange.getEndOffset());
    return StringUtil.join(fragment.split("\\s"), " ");
  }

  private static List<LineFragment> getLineFragments(@NotNull String beforeContent, @NotNull String afterContent)
    throws DiffTooBigException {
    ProgressIndicator indicator = notNull(ProgressManager.getInstance().getProgressIndicator(), DumbProgressIndicator.INSTANCE);
    return ComparisonManager.getInstance().compareLines(beforeContent, afterContent, ComparisonPolicy.DEFAULT, indicator);
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
    TextRange textRange = o.getTextRange();
    List<TextRange> additionalRanges = o.getAdditionalTextRanges();
    return new TextRange(textRange.getStartOffset(),
                         (additionalRanges.isEmpty() ? textRange : additionalRanges.get(additionalRanges.size() - 1)).getEndOffset() - 1);
  };

  private static class LeftLineFragmentConvertor implements Convertor<LineFragment, TextRange> {
    private final String myContent;

    private LeftLineFragmentConvertor(String content) {myContent = content;}

    @Override
    public TextRange convert(LineFragment o) {
      int start = o.getStartOffset1();
      int end = o.getEndOffset1();
      int prevLineStart = myContent.lastIndexOf('\n', start - 2) + 1;
      return new TextRange(prevLineStart, Math.max(start, end - 1));
    }
  }

  private static class RightLineFragmentConvertor implements Convertor<LineFragment, TextRange> {
    private final String myContent;

    private RightLineFragmentConvertor(String content) {myContent = content;}

    @Override
    public TextRange convert(LineFragment o) {
      int start = o.getStartOffset2();
      int end = o.getEndOffset2();
      int prevLineStart = myContent.lastIndexOf('\n', start - 2) + 1;
      return new TextRange(prevLineStart, Math.max(start, end - 1));
    }
  }

  public Set<TodoItem> inOneList() {
    final Set<TodoItem> set = new HashSet<>();
    set.addAll(getAddedOrEditedTodos());
    set.addAll(getInChangedTodos());
    return set;
  }
}
