/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.external.DiffManagerImpl;
import com.intellij.openapi.diff.impl.fragments.LineFragment;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.processing.TextCompareProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.actions.ShowDiffAction;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManagerI;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BeforeAfter;
import com.intellij.util.containers.SLRUMap;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author irengrig
 *         Date: 6/15/11
 *         Time: 6:02 PM
 */
public class FragmentedDiffRequestFromChange {
  private final Project myProject;
  private final SLRUMap<Pair<Long, String>, List<BeforeAfter<TextRange>>> myRangesCache;
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.FragmentedDiffRequestFromChange");

  public FragmentedDiffRequestFromChange(Project project) {
    myProject = project;
    myRangesCache = new SLRUMap<Pair<Long, String>, List<BeforeAfter<TextRange>>>(10, 10);
  }

  public static boolean canCreateRequest(Change change) {
    if (ChangesUtil.isTextConflictingChange(change) || change.isTreeConflict() || change.isPhantom()) return false;
    if (ShowDiffAction.isBinaryChange(change)) return false;
    final FilePath filePath = ChangesUtil.getFilePath(change);
    if (filePath.isDirectory()) return false;
    return true;
  }

  public PreparedFragmentedContent getRanges(Change change) throws VcsException {
    FilePath filePath = ChangesUtil.getFilePath(change);

    final RangesCalculator calculator = new RangesCalculator();
    calculator.execute(change, filePath, myRangesCache, LineStatusTrackerManager.getInstance(myProject));
    final VcsException exception = calculator.getException();
    if (exception != null) {
      LOG.info(exception);
      throw exception;
    }
    List<BeforeAfter<TextRange>> ranges = calculator.getRanges();
    if (ranges == null || ranges.isEmpty()) return null;
    FragmentedContent fragmentedContent = new FragmentedContent(calculator.getOldDocument(), calculator.getDocument(), ranges, change);
    VirtualFile file = filePath.getVirtualFile();
    if (file == null) {
      file = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath.getPath());
    }
    return new PreparedFragmentedContent(myProject, fragmentedContent,
                                    filePath.getName(), filePath.getFileType(),
                                    change.getBeforeRevision() == null ? null : change.getBeforeRevision().getRevisionNumber(),
                                    change.getAfterRevision() == null ? null : change.getAfterRevision().getRevisionNumber(), filePath, file);
  }

  private static class RangesCalculator {
    private List<BeforeAfter<TextRange>> myRanges;
    private VcsException myException;
    private Document myDocument;
    private Document myOldDocument;

    public Document getDocument() {
      return myDocument;
    }

    public Document getOldDocument() {
      return myOldDocument;
    }

    public void execute(final Change change,
                        final FilePath filePath,
                        final SLRUMap<Pair<Long, String>, List<BeforeAfter<TextRange>>> cache,
                        final LineStatusTrackerManagerI lstManager) {
      try {
        myDocument = null;
        myOldDocument = documentFromRevision(change.getBeforeRevision());
        final String convertedPath = FilePathsHelper.convertPath(filePath);
        if (filePath.getVirtualFile() != null) {
          myDocument = FileStatus.DELETED.equals(change.getFileStatus())
                       ? new DocumentImpl("")
                       : FileDocumentManager.getInstance().getDocument(filePath.getVirtualFile());
          if (myDocument != null) {
            final List<BeforeAfter<TextRange>> cached = cache.get(new Pair<Long, String>(myDocument.getModificationStamp(), convertedPath));
            if (cached != null) {
              myRanges = cached;
              return;
            }
          }
        }

        if (myDocument == null) {
          myDocument = documentFromRevision(change.getAfterRevision());
          final List<BeforeAfter<TextRange>> cached = cache.get(new Pair<Long, String>(-1L, convertedPath));
          if (cached != null) {
            myRanges = cached;
            return;
          }
        }

        TextCompareProcessor processor = new TextCompareProcessor(DiffManagerImpl.getInstanceEx().getComparisonPolicy());
        List<LineFragment> lineFragments = processor.process(myOldDocument.getText(), myDocument.getText());
        myRanges = new ArrayList<BeforeAfter<TextRange>>(lineFragments.size());
        for (LineFragment lineFragment : lineFragments) {
          if (!lineFragment.isEqual()) {
            final TextRange oldRange = lineFragment.getRange(FragmentSide.SIDE1);
            final TextRange newRange = lineFragment.getRange(FragmentSide.SIDE2);
            int beforeBegin = myOldDocument.getLineNumber(oldRange.getStartOffset());
            int beforeEnd = myOldDocument.getLineNumber(correctRangeEnd(oldRange.getEndOffset(), myOldDocument));
            int afterBegin = myDocument.getLineNumber(newRange.getStartOffset());
            int afterEnd = myDocument.getLineNumber(correctRangeEnd(newRange.getEndOffset(), myDocument));
            if (oldRange.isEmpty()) {
              beforeEnd = beforeBegin - 1;
            }
            if (newRange.isEmpty()) {
              afterEnd = afterBegin - 1;
            }
            myRanges
              .add(new BeforeAfter<TextRange>(new UnfairTextRange(beforeBegin, beforeEnd), new UnfairTextRange(afterBegin, afterEnd)));
          }
        }
        cache
          .put(new Pair<Long, String>(myDocument.getModificationStamp(), convertedPath), new ArrayList<BeforeAfter<TextRange>>(myRanges));
      }
      catch (VcsException e) {
        myException = e;
      }
      catch (FilesTooBigForDiffException e) {
        myException = new VcsException(e);
      }
    }
    
    private static int correctRangeEnd(final int end, final Document document) {
      if (end == 0) return end;
      return "\n".equals(document.getText(new TextRange(end - 1, end))) ? end - 1 : end;
    }

    public List<BeforeAfter<TextRange>> getRanges() {
      return myRanges;
    }

    public VcsException getException() {
      return myException;
    }

    private static Document documentFromRevision(final ContentRevision cr) throws VcsException {
      final Document oldDocument = new DocumentImpl(StringUtil.convertLineSeparators(notNullContentRevision(cr)),true);
      // todo !!! a question how to show line separators in diff etc
      // todo currently document doesn't allow to put \r as separator
      oldDocument.setReadOnly(true);
      return oldDocument;
    }

    private static String notNullContentRevision(final ContentRevision cr) throws VcsException {
      if (cr == null) return "";
      String content = cr.getContent();
      return content == null ? "" : content;
    }
  }

  private static class MyWorker {
    private final Document myDocument;
    private final Document myOldDocument;
    private final List<Range> myRanges;

    private MyWorker(Document document, Document oldDocument, final List<Range> ranges) {
      myDocument = document;
      myOldDocument = oldDocument;
      myRanges = ranges;
    }

    @Nullable
    public List<BeforeAfter<TextRange>> execute() {
      final List<BeforeAfter<TextRange>> result = new ArrayList<BeforeAfter<TextRange>>();
      if (myRanges == null || myRanges.isEmpty()) return Collections.emptyList();
      for (Range range : myRanges) {
        final TextRange before = new TextRange(range.getVcsLine1(), range.getVcsLine2());
        final TextRange after = new TextRange(range.getLine1(), range.getLine2());
        result.add(new BeforeAfter<TextRange>(before, after));
      }
      return result;
    }
  }
}
