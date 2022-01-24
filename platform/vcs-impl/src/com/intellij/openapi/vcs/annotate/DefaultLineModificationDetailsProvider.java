// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.annotate;

import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.tools.util.text.LineOffsets;
import com.intellij.diff.tools.util.text.LineOffsetsUtil;
import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.AnnotatedLineModificationDetails.InnerChange;
import com.intellij.openapi.vcs.annotate.AnnotatedLineModificationDetails.InnerChangeType;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsImplUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonList;

public class DefaultLineModificationDetailsProvider implements FileAnnotation.LineModificationDetailsProvider {
  @NotNull private final FileAnnotation myAnnotation;
  @NotNull private final FilePath myFilePath;
  @NotNull private final FileAnnotation.CurrentFileRevisionProvider myCurrentRevisionProvider;
  @NotNull private final FileAnnotation.PreviousFileRevisionProvider myPreviousRevisionProvider;

  private DefaultLineModificationDetailsProvider(@NotNull FileAnnotation annotation,
                                                 @NotNull FilePath filePath,
                                                 @NotNull FileAnnotation.CurrentFileRevisionProvider currentRevisionProvider,
                                                 @NotNull FileAnnotation.PreviousFileRevisionProvider previousRevisionProvider) {
    myAnnotation = annotation;
    myFilePath = filePath;
    myCurrentRevisionProvider = currentRevisionProvider;
    myPreviousRevisionProvider = previousRevisionProvider;
  }

  @Nullable
  public static FileAnnotation.LineModificationDetailsProvider create(@NotNull FileAnnotation annotation) {
    VirtualFile file = annotation.getFile();
    if (file == null) return null;

    FileAnnotation.CurrentFileRevisionProvider currentRevisionProvider = annotation.getCurrentFileRevisionProvider();
    FileAnnotation.PreviousFileRevisionProvider previousRevisionProvider = annotation.getPreviousFileRevisionProvider();
    if (currentRevisionProvider == null || previousRevisionProvider == null) return null;

    FilePath filePath = VcsUtil.getFilePath(file);
    return new DefaultLineModificationDetailsProvider(annotation, filePath, currentRevisionProvider, previousRevisionProvider);
  }

  @Override
  public @Nullable AnnotatedLineModificationDetails getDetails(int lineNumber) throws VcsException {
    String annotatedContent = myAnnotation.getAnnotatedContent();
    if (annotatedContent == null) return null;

    LineOffsets offsets = LineOffsetsUtil.create(annotatedContent);
    String originalLine = getLine(annotatedContent, offsets, lineNumber);

    VcsFileRevision afterRevision = myCurrentRevisionProvider.getRevision(lineNumber);
    String afterContent = loadRevision(myAnnotation.getProject(), afterRevision, myFilePath);
    if (afterContent == null) return null;

    VcsFileRevision beforeRevision = myPreviousRevisionProvider.getPreviousRevision(lineNumber);
    String beforeContent = loadRevision(myAnnotation.getProject(), beforeRevision, myFilePath);
    if (beforeContent == null) {
      return createNewLineDetails(originalLine); // the whole file is new. Skip searching for the original line.
    }

    return createDetailsFor(beforeContent, afterContent, originalLine);
  }

  @Nullable
  public static String loadRevision(@Nullable Project project,
                                    @Nullable VcsFileRevision revision,
                                    @NotNull FilePath filePath) throws VcsException {
    try {
      if (revision == null) return null;
      byte[] bytes = revision.loadContent();
      if (bytes == null) return null;
      String content = VcsImplUtil.loadTextFromBytes(project, bytes, filePath);
      return StringUtil.convertLineSeparators(content);
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }


  @Nullable
  public static AnnotatedLineModificationDetails createDetailsFor(@NotNull String beforeContent,
                                                                  @NotNull String afterContent,
                                                                  @NotNull String originalLine) {
    List<LineFragment> fragments = compareContents(beforeContent, afterContent);

    LineOffsets afterLineOffsets = LineOffsetsUtil.create(afterContent);
    int originalLineNumber = findOriginalLine(afterContent, afterLineOffsets, originalLine, fragments);
    if (originalLineNumber == -1) {
      return null; // line not found
    }

    String lineContentAfter = getLine(afterContent, afterLineOffsets, originalLineNumber);

    if (StringUtil.isEmptyOrSpaces(lineContentAfter)) {
      return createNewLineDetails(lineContentAfter); // empty lines are always new, for simplicity
    }

    return createFragmentDetails(lineContentAfter, afterLineOffsets, fragments, originalLineNumber);
  }

  @Nullable
  public static AnnotatedLineModificationDetails createDetailsFor(@Nullable String beforeContent,
                                                                  @NotNull String afterContent,
                                                                  int originalLineNumber) {
    LineOffsets afterLineOffsets = LineOffsetsUtil.create(afterContent);
    String lineContentAfter = getLine(afterContent, afterLineOffsets, originalLineNumber);

    if (beforeContent == null) {
      return createNewLineDetails(lineContentAfter); // the whole file is new
    }

    if (StringUtil.isEmptyOrSpaces(lineContentAfter)) {
      return createNewLineDetails(lineContentAfter); // empty lines are always new, for simplicity
    }

    List<LineFragment> fragments = compareContents(beforeContent, afterContent);
    return createFragmentDetails(lineContentAfter, afterLineOffsets, fragments, originalLineNumber);
  }

  @Nullable
  private static AnnotatedLineModificationDetails createFragmentDetails(@NotNull String lineContentAfter,
                                                                        @NotNull LineOffsets afterLineOffsets,
                                                                        @NotNull List<LineFragment> fragments,
                                                                        int originalLineNumber) {
    LineFragment lineFragment = ContainerUtil.find(fragments.iterator(), fragment -> {
      return fragment.getStartLine2() <= originalLineNumber && originalLineNumber < fragment.getEndLine2();
    });
    if (lineFragment == null) return null; // line unmodified

    if (lineFragment.getStartLine1() == lineFragment.getEndLine1()) {
      return createNewLineDetails(lineContentAfter); // the whole line is new
    }

    List<DiffFragment> innerFragments = lineFragment.getInnerFragments();
    if (innerFragments == null) {
      return createModifiedLineDetails(lineContentAfter); // the whole line is modified
    }

    int lineStart = afterLineOffsets.getLineStart(originalLineNumber);
    int lineEnd = afterLineOffsets.getLineEnd(originalLineNumber);
    int windowStart = lineStart - lineFragment.getStartOffset2();
    int windowEnd = lineEnd - lineFragment.getStartOffset2();
    int lineLength = lineEnd - lineStart;

    List<InnerChange> changes = new ArrayList<>();
    for (DiffFragment innerFragment : innerFragments) {
      if (innerFragment.getEndOffset2() < windowStart || innerFragment.getStartOffset2() > windowEnd) continue;
      int start = Math.max(0, innerFragment.getStartOffset2() - windowStart);
      int end = Math.min(lineLength, innerFragment.getEndOffset2() - windowStart);
      InnerChangeType type = start == end ? InnerChangeType.DELETED
                                          : innerFragment.getStartOffset1() != innerFragment.getEndOffset1() ? InnerChangeType.MODIFIED
                                                                                                             : InnerChangeType.INSERTED;
      changes.add(new InnerChange(start, end, type));
    }

    return new AnnotatedLineModificationDetails(lineContentAfter, changes);
  }

  @NotNull
  public static AnnotatedLineModificationDetails createNewLineDetails(@NotNull String lineContentAfter) {
    InnerChange innerChange = new InnerChange(0, lineContentAfter.length(), InnerChangeType.INSERTED);
    return new AnnotatedLineModificationDetails(lineContentAfter, singletonList(innerChange));
  }

  @NotNull
  public static AnnotatedLineModificationDetails createModifiedLineDetails(@NotNull String lineContentAfter) {
    InnerChange innerChange = new InnerChange(0, lineContentAfter.length(), InnerChangeType.MODIFIED);
    return new AnnotatedLineModificationDetails(lineContentAfter, singletonList(innerChange));
  }

  @NotNull
  private static List<LineFragment> compareContents(@NotNull String beforeContent, @NotNull String afterContent) {
    ProgressIndicator indicator = ObjectUtils.chooseNotNull(ProgressIndicatorProvider.getGlobalProgressIndicator(),
                                                            DumbProgressIndicator.INSTANCE);
    return ComparisonManager.getInstance().compareLinesInner(beforeContent, afterContent, ComparisonPolicy.DEFAULT, indicator);
  }

  /**
   * Search for affected line in content after the commit.
   *
   * @see DiffNavigationContext
   */
  private static int findOriginalLine(@NotNull String afterContent,
                                      @NotNull LineOffsets afterLineOffsets,
                                      @NotNull String originalLine,
                                      @NotNull List<LineFragment> fragments) {
    for (LineFragment fragment : fragments) {
      for (int i = fragment.getStartLine2(); i < fragment.getEndLine2(); i++) {
        String line = getLine(afterContent, afterLineOffsets, i);
        if (StringUtil.equalsIgnoreWhitespaces(line, originalLine)) return i;
      }
    }

    return -1; // line not found
  }

  @NotNull
  private static String getLine(@NotNull String text, @NotNull LineOffsets lineOffsets, int line) {
    int lineStart = lineOffsets.getLineStart(line);
    int lineEnd = lineOffsets.getLineEnd(line);
    return text.substring(lineStart, lineEnd);
  }
}
