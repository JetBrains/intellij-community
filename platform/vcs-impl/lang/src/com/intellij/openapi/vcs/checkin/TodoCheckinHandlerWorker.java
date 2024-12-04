// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkin;

import com.intellij.codeInsight.actions.ChangedRangesShifter;
import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonManagerImpl;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.comparison.iterables.DiffIterable;
import com.intellij.diff.comparison.iterables.DiffIterableUtil;
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.tools.util.text.LineOffsets;
import com.intellij.diff.tools.util.text.LineOffsetsUtil;
import com.intellij.diff.util.Range;
import com.intellij.ide.todo.TodoFilter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.ex.RangesBuilder;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.util.SmartList;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.progress.ProgressIndicatorProvider.getGlobalProgressIndicator;
import static com.intellij.openapi.progress.ProgressManager.progress;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.filter;
import static java.util.Objects.requireNonNull;

@ApiStatus.Internal
public class TodoCheckinHandlerWorker {
  private final static Logger LOG = Logger.getInstance(TodoCheckinHandlerWorker.class);

  private final Project myProject;
  private final Collection<? extends Change> myChanges;
  private final TodoFilter myTodoFilter;

  private final Set<TodoItem> myAddedOrEditedTodos = new HashSet<>();
  private final Set<TodoItem> myInChangedTodos = new HashSet<>();
  private final List<Pair<FilePath, String>> mySkipped = new SmartList<>();

  public TodoCheckinHandlerWorker(@NotNull Project project,
                                  @NotNull Collection<? extends Change> changes,
                                  @Nullable TodoFilter todoFilter) {
    myProject = project;
    myChanges = changes;
    myTodoFilter = todoFilter;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public void execute() {
    List<? extends Change> changes = filter(myChanges, it -> it.getAfterRevision() != null);

    for (int i = 0; i < changes.size(); i++) {
      Change change = changes.get(i);
      ContentRevision beforeRevision = change.getBeforeRevision();
      ContentRevision afterRevision = requireNonNull(change.getAfterRevision());
      FilePath afterFilePath = afterRevision.getFile();
      VirtualFile afterFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(afterFilePath.getPath());

      EditedFileProcessorBase fileProcessor = ReadAction.compute(() -> {
        progress(afterFile != null ? ProjectUtil.calcRelativeToProjectPath(afterFile, myProject) : afterFilePath.getName());

        if (afterFile == null || afterFile.isDirectory() || afterFile.getFileType().isBinary()) {
          return null; // skip detection
        }

        PsiFile afterPsiFile = afterFile.isValid() ? PsiManager.getInstance(myProject).findFile(afterFile) : null;
        if (afterPsiFile == null) {
          mySkipped.add(Pair.create(afterFilePath, VcsBundle.message("checkin.invalid.file.s")));
          return null;
        }

        if (beforeRevision == null) {
          // take just all todos
          List<TodoItem> newTodoItems = collectTodoItems(afterPsiFile, false);
          myAddedOrEditedTodos.addAll(newTodoItems);
          return null;
        }

        Document afterDocument = FileDocumentManager.getInstance().getDocument(afterFile);
        if (afterDocument == null) {
          mySkipped.add(Pair.create(afterFilePath, VcsBundle.message("checkin.can.not.load.current.revision")));
          return null;
        }
        String afterContent = afterDocument.getText();
        List<? extends TodoItem> afterTodoItems = collectTodoItems(afterPsiFile, false);

        if (afterRevision instanceof CurrentContentRevision) {
          return new SimpleEditedFileProcessor(myProject, afterFilePath, beforeRevision, afterContent, afterTodoItems);
        }
        else {
          return new NonLocalEditedFileProcessor(myProject, afterFilePath, beforeRevision, afterRevision, afterContent, afterTodoItems);
        }
      });

      try {
        if (fileProcessor != null) fileProcessor.process();
        fraction((i + 1.0) / changes.size());
      }
      catch (DiffTooBigException e) {
        LOG.info("File " + afterFilePath.getPath() + " is too big and there are too many changes to build a diff");
        mySkipped.add(Pair.create(afterFilePath, VcsBundle.message("checkin.can.not.load.previous.revision")));
      }
    }
  }

  private static void fraction(double value) {
    ProgressIndicator indicator = getGlobalProgressIndicator();
    if (indicator != null) {
      indicator.setFraction(value);
    }
  }

  @NotNull
  private List<TodoItem> collectTodoItems(@NotNull PsiFile psiFile, boolean isLight) {
    if (!isLight) ApplicationManager.getApplication().assertReadAccessAllowed();
    PsiTodoSearchHelper searchHelper = PsiTodoSearchHelper.getInstance(myProject);
    TodoItem[] todoItems = isLight ? searchHelper.findTodoItemsLight(psiFile)
                                   : searchHelper.findTodoItems(psiFile);
    return applyFilterAndRemoveDuplicatesAndSort(todoItems, myTodoFilter);
  }

  private static List<TodoItem> applyFilterAndRemoveDuplicatesAndSort(TodoItem @NotNull [] items, @Nullable TodoFilter filter) {
    record ByRange(@NotNull TextRange range, @NotNull TodoItem item) {
      @Override
      public boolean equals(Object obj) {
        return obj instanceof ByRange br && range.equals(br.range);
      }
      @Override
      public int hashCode() {
        return range.hashCode();
      }
    }
    return Arrays.stream(items)
      .filter(next -> filter == null || (next.getPattern() != null && filter.contains(next.getPattern())))
      .map(p->new ByRange(p.getTextRange(), p))
      .distinct()
      .map(br -> br.item())
      .sorted(TodoItem.BY_START_OFFSET)
      .toList();
  }

  private final class SimpleEditedFileProcessor extends EditedFileProcessorBase {
    private final ContentRevision myBeforeRevision;

    private SimpleEditedFileProcessor(@NotNull Project project,
                                      @NotNull FilePath afterFilePath,
                                      @NotNull ContentRevision beforeRevision,
                                      @NotNull String afterContent,
                                      @NotNull List<? extends TodoItem> afterTodoItems) {
      super(project, afterFilePath, afterContent, afterTodoItems);
      myBeforeRevision = beforeRevision;
    }

    @Override
    protected boolean loadContents() {
      myBeforeContent = getRevisionContent(myBeforeRevision);
      if (myBeforeContent == null) {
        mySkipped.add(Pair.create(myAfterFile, VcsBundle.message("checkin.can.not.load.previous.revision")));
        return false;
      }
      return true;
    }

    @Override
    protected @NotNull List<LineFragment> computeFragments() {
      ProgressIndicator indicator = notNull(ProgressManager.getInstance().getProgressIndicator(), DumbProgressIndicator.INSTANCE);
      return ComparisonManager.getInstance().compareLines(myBeforeContent, myAfterContent, ComparisonPolicy.DEFAULT, indicator);
    }
  }

  private final class NonLocalEditedFileProcessor extends EditedFileProcessorBase {
    private final ContentRevision myBeforeRevision;
    private final ContentRevision myAfterChangeRevision;

    private String myAfterChangeContent;

    private NonLocalEditedFileProcessor(@NotNull Project project,
                                        @NotNull FilePath afterFilePath,
                                        @NotNull ContentRevision beforeRevision,
                                        @NotNull ContentRevision afterChangeRevision,
                                        @NotNull String afterContent,
                                        @NotNull List<? extends TodoItem> afterTodoItems) {
      super(project, afterFilePath, afterContent, afterTodoItems);
      myBeforeRevision = beforeRevision;
      myAfterChangeRevision = afterChangeRevision;
    }

    @Override
    protected boolean loadContents() {
      myBeforeContent = getRevisionContent(myBeforeRevision);
      if (myBeforeContent == null) {
        mySkipped.add(Pair.create(myAfterFile, VcsBundle.message("checkin.can.not.load.previous.revision")));
        return false;
      }

      myAfterChangeContent = getRevisionContent(myAfterChangeRevision);
      if (myAfterChangeContent == null) {
        mySkipped.add(Pair.create(myAfterFile, VcsBundle.message("checkin.can.not.load.current.revision")));
        return false;
      }

      return true;
    }

    @Override
    protected @NotNull List<LineFragment> computeFragments() {
      LineOffsets beforeLineOffsets = LineOffsetsUtil.create(myBeforeContent);
      LineOffsets afterChangeLineOffsets = LineOffsetsUtil.create(myAfterChangeContent);
      LineOffsets afterLineOffsets = LineOffsetsUtil.create(myAfterContent);

      FairDiffIterable committedLines = RangesBuilder.compareLines(myBeforeContent, myAfterChangeContent,
                                                                   beforeLineOffsets, afterChangeLineOffsets);
      FairDiffIterable localLines = RangesBuilder.compareLines(myAfterChangeContent, myAfterContent,
                                                               afterChangeLineOffsets, afterLineOffsets);

      ChangedRangesShifter builder = new ChangedRangesShifter();
      List<Range> ranges = builder.execute(committedLines, localLines);

      DiffIterable iterable = DiffIterableUtil.create(ranges, beforeLineOffsets.getLineCount(), afterLineOffsets.getLineCount());
      return ComparisonManagerImpl.convertIntoLineFragments(beforeLineOffsets, afterLineOffsets, iterable);
    }
  }

  private abstract class EditedFileProcessorBase {
    @NotNull protected final Project myProject;
    protected String myBeforeContent;
    @NotNull protected final String myAfterContent;
    @NotNull protected final FilePath myAfterFile;
    @NotNull private final List<? extends TodoItem> myNewTodoItems;

    private EditedFileProcessorBase(@NotNull Project project,
                                    @NotNull FilePath afterFilePath,
                                    @NotNull String afterContent,
                                    @NotNull List<? extends TodoItem> afterTodoItems) {
      myProject = project;
      myAfterFile = afterFilePath;
      myAfterContent = afterContent;
      myNewTodoItems = afterTodoItems;
    }

    protected abstract boolean loadContents();

    protected abstract @NotNull List<LineFragment> computeFragments();

    private @NotNull List<TodoItem> computeOldTodoItems() {
      PsiFile beforePsiFile = ReadAction.compute(
        () -> PsiFileFactory.getInstance(myProject).createFileFromText("old" + myAfterFile.getName(),
                                                                       myAfterFile.getFileType(), myBeforeContent));
      return collectTodoItems(beforePsiFile, true);
    }

    public void process() throws DiffTooBigException {
      if (!loadContents()) return;

      List<LineFragment> lineFragments = computeFragments();

      List<Pair<TodoItem, LineFragment>> changedTodoItems = new ArrayList<>();
      StepIntersection.processIntersections(
        myNewTodoItems, lineFragments,
        TODO_ITEM_CONVERTOR, new RightLineFragmentConvertor(myAfterContent),
        (todoItem, lineFragment) -> changedTodoItems.add(Pair.create(todoItem, lineFragment)));

      if (changedTodoItems.isEmpty()) return;

      final List<TodoItem> oldTodoItems = computeOldTodoItems();

      if (oldTodoItems.isEmpty()) {
        for (Pair<TodoItem, LineFragment> pair : changedTodoItems) {
          myAddedOrEditedTodos.add(pair.first);
        }
        return;
      }

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

  @NotNull
  public Set<TodoItem> getAddedOrEditedTodos() {
    return myAddedOrEditedTodos;
  }

  @NotNull
  public Set<TodoItem> getInChangedTodos() {
    return myInChangedTodos;
  }

  @NotNull
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
    final String fragment = textRange.substring(content);
    return StringUtil.join(fragment.split("\\s"), " "); //NON-NLS
  }

  @Nullable
  private static String getRevisionContent(@NotNull ContentRevision revision) {
    try {
      String content = revision.getContent();
      return content != null ? StringUtil.convertLineSeparators(content) : null;
    }
    catch (VcsException e) {
      LOG.info(e);
      return null;
    }
  }

  private static final Convertor<TodoItem, TextRange> TODO_ITEM_CONVERTOR = o -> {
    TextRange textRange = o.getTextRange();
    List<TextRange> additionalRanges = o.getAdditionalTextRanges();
    return new TextRange(textRange.getStartOffset(),
                         (additionalRanges.isEmpty() ? textRange : additionalRanges.get(additionalRanges.size() - 1)).getEndOffset() - 1);
  };

  private static final class LeftLineFragmentConvertor implements Convertor<LineFragment, TextRange> {
    private final String myContent;

    private LeftLineFragmentConvertor(String content) { myContent = content; }

    @Override
    public TextRange convert(LineFragment o) {
      int start = o.getStartOffset1();
      int end = o.getEndOffset1();
      int prevLineStart = myContent.lastIndexOf('\n', start - 2) + 1;
      return new TextRange(prevLineStart, Math.max(start, end - 1));
    }
  }

  private static final class RightLineFragmentConvertor implements Convertor<LineFragment, TextRange> {
    private final String myContent;

    private RightLineFragmentConvertor(String content) { myContent = content; }

    @Override
    public TextRange convert(LineFragment o) {
      int start = o.getStartOffset2();
      int end = o.getEndOffset2();
      int prevLineStart = myContent.lastIndexOf('\n', start - 2) + 1;
      return new TextRange(prevLineStart, Math.max(start, end - 1));
    }
  }

  @NotNull
  public Set<TodoItem> inOneList() {
    final Set<TodoItem> set = new HashSet<>();
    set.addAll(getAddedOrEditedTodos());
    set.addAll(getInChangedTodos());
    return set;
  }

  @NotNull
  public List<Change> getChanges() {
    return new ArrayList<>(myChanges);
  }
}
