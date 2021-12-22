// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.diff.comparison.ByLine;
import com.intellij.diff.comparison.CancellationChecker;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.util.Range;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.BeforeAfter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.io.File;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.singletonList;

public final class TextPatchBuilder {
  private static final int CONTEXT_LINES = 3;
  /**
   * @see com.intellij.openapi.vcs.changes.patch.DefaultPatchBaseVersionProvider
   */
  @NonNls private static final String REVISION_NAME_TEMPLATE = "(revision {0})";
  @NonNls private static final String DATE_NAME_TEMPLATE = "(date {0})";

  @NotNull private final Path myBasePath;
  private final boolean myIsReversePath;
  private final int myContextLineCount = Registry.get("patch.context.line.count").asInteger();
  @Nullable private final Runnable myCancelChecker;

  private TextPatchBuilder(@NotNull Path basePath,
                           boolean isReversePath,
                           @Nullable Runnable cancelChecker) {
    myBasePath = basePath;
    myIsReversePath = isReversePath;
    myCancelChecker = cancelChecker;
  }

  public static @NotNull List<FilePatch> buildPatch(@NotNull Collection<BeforeAfter<AirContentRevision>> changes,
                                                    @NotNull Path basePath,
                                                    boolean reversePatch,
                                                    @Nullable Runnable cancelChecker) throws VcsException {
    return new TextPatchBuilder(basePath, reversePatch, cancelChecker).build(changes);
  }

  @NotNull
  private List<FilePatch> build(@NotNull Collection<BeforeAfter<AirContentRevision>> changes) throws VcsException {
    List<FilePatch> result = new ArrayList<>();
    for (BeforeAfter<AirContentRevision> c : changes) {
      if (myCancelChecker != null) myCancelChecker.run();

      AirContentRevision beforeRevision = myIsReversePath ? c.getAfter() : c.getBefore();
      AirContentRevision afterRevision = myIsReversePath ? c.getBefore() : c.getAfter();

      FilePatch patch = createPatch(beforeRevision, afterRevision);
      if (patch != null) result.add(patch);
    }
    return result;
  }

  @Nullable
  private FilePatch createPatch(@Nullable AirContentRevision beforeRevision,
                                @Nullable AirContentRevision afterRevision)
    throws VcsException {
    if (beforeRevision == null && afterRevision == null) return null;

    if (beforeRevision != null && beforeRevision.getPath().isDirectory()) return null;
    if (afterRevision != null && afterRevision.getPath().isDirectory()) return null;

    if (beforeRevision != null && beforeRevision.isBinary() ||
        afterRevision != null && afterRevision.isBinary()) {
      return buildBinaryPatch(beforeRevision, afterRevision);
    }

    if (beforeRevision == null) {
      return buildAddedFile(afterRevision);
    }
    if (afterRevision == null) {
      return buildDeletedFile(beforeRevision);
    }

    return buildModifiedFile(beforeRevision, afterRevision);
  }

  @Nullable
  private TextFilePatch buildModifiedFile(@NotNull AirContentRevision beforeRevision,
                                          @NotNull AirContentRevision afterRevision) throws VcsException {
    String beforeContent = getContent(beforeRevision);
    String afterContent = getContent(afterRevision);

    TextFilePatch patch = buildPatchHeading(beforeRevision, afterRevision);

    List<PatchHunk> hunks = buildPatchHunks(beforeContent, afterContent, myContextLineCount);
    for (PatchHunk hunk : hunks) {
      patch.addHunk(hunk);
    }

    // skip empty patch
    if (hunks.isEmpty() && beforeRevision.getPath().equals(afterRevision.getPath())) return null;

    return patch;
  }

  @SuppressWarnings("unused")
  @NotNull
  public static List<PatchHunk> buildPatchHunks(@NotNull String beforeContent, @NotNull String afterContent) {
    return buildPatchHunks(beforeContent, afterContent, CONTEXT_LINES);
  }

  @NotNull
  public static List<PatchHunk> buildPatchHunks(@NotNull String beforeContent, @NotNull String afterContent, int contextLineCount) {
    if (beforeContent.equals(afterContent)) return Collections.emptyList();
    if (beforeContent.isEmpty()) {
      return singletonList(createWholeFileHunk(afterContent, true, true));
    }
    if (afterContent.isEmpty()) {
      return singletonList(createWholeFileHunk(beforeContent, false, true));
    }

    List<PatchHunk> hunks = new ArrayList<>();

    List<String> beforeLines = tokenize(beforeContent);
    List<String> afterLines = tokenize(afterContent);
    boolean beforeNoNewlineAtEOF = !beforeContent.endsWith("\n");
    boolean afterNoNewlineAtEOF = !afterContent.endsWith("\n");

    List<Range> fragments = compareLines(beforeLines, afterLines, beforeNoNewlineAtEOF, afterNoNewlineAtEOF);

    int hunkStart = 0;
    while (hunkStart < fragments.size()) {
      List<Range> hunkFragments = getAdjacentFragments(fragments, hunkStart, contextLineCount);

      hunks.add(createHunk(hunkFragments, beforeLines, afterLines, beforeNoNewlineAtEOF, afterNoNewlineAtEOF, contextLineCount));

      hunkStart += hunkFragments.size();
    }

    return hunks;
  }

  @NotNull
  private static List<Range> getAdjacentFragments(@NotNull List<Range> fragments, int hunkStart, int contextLineCount) {
    int hunkEnd = hunkStart + 1;
    while (hunkEnd < fragments.size()) {
      Range lastFragment = fragments.get(hunkEnd - 1);
      Range nextFragment = fragments.get(hunkEnd);

      if (lastFragment.end1 + contextLineCount < nextFragment.start1 - contextLineCount &&
          lastFragment.end2 + contextLineCount < nextFragment.start2 - contextLineCount) {
        break;
      }
      hunkEnd++;
    }
    return fragments.subList(hunkStart, hunkEnd);
  }

  @NotNull
  private static PatchHunk createHunk(@NotNull List<? extends Range> hunkFragments,
                                      @NotNull List<String> beforeLines,
                                      @NotNull List<String> afterLines,
                                      boolean beforeNoNewlineAtEOF,
                                      boolean afterNoNewlineAtEOF,
                                      int contextLineCount) {
    Range first = hunkFragments.get(0);
    Range last = hunkFragments.get(hunkFragments.size() - 1);

    int contextStart1 = Math.max(first.start1 - contextLineCount, 0);
    int contextStart2 = Math.max(first.start2 - contextLineCount, 0);
    int contextEnd1 = Math.min(last.end1 + contextLineCount, beforeLines.size());
    int contextEnd2 = Math.min(last.end2 + contextLineCount, afterLines.size());

    PatchHunk hunk = new PatchHunk(contextStart1, contextEnd1, contextStart2, contextEnd2);

    int lastLine1 = contextStart1;
    int lastLine2 = contextStart2;
    for (Range fragment : hunkFragments) {
      int start1 = fragment.start1;
      int start2 = fragment.start2;
      int end1 = fragment.end1;
      int end2 = fragment.end2;
      assert start1 - lastLine1 == start2 - lastLine2;

      for (int i = lastLine1; i < start1; i++) {
        addLineToHunk(hunk, beforeLines, PatchLine.Type.CONTEXT, i, beforeNoNewlineAtEOF);
      }
      for (int i = start1; i < end1; i++) {
        addLineToHunk(hunk, beforeLines, PatchLine.Type.REMOVE, i, beforeNoNewlineAtEOF);
      }
      for (int i = start2; i < end2; i++) {
        addLineToHunk(hunk, afterLines, PatchLine.Type.ADD, i, afterNoNewlineAtEOF);
      }
      lastLine1 = end1;
      lastLine2 = end2;
    }
    assert contextEnd1 - lastLine1 == contextEnd2 - lastLine2;
    for (int i = lastLine1; i < contextEnd1; i++) {
      addLineToHunk(hunk, beforeLines, PatchLine.Type.CONTEXT, i, beforeNoNewlineAtEOF);
    }

    return hunk;
  }

  @NotNull
  private static List<Range> compareLines(@NotNull List<String> beforeLines,
                                          @NotNull List<String> afterLines,
                                          boolean beforeNoNewlineAtEOF,
                                          boolean afterNoNewlineAtEOF) {
    // patch treats "X\n" vs "X" as modification of a single line, while we treat it as a deletion of an empty line
    // so we have to adjust output accordingly

    if (!beforeNoNewlineAtEOF && !afterNoNewlineAtEOF) return doCompareLines(beforeLines, afterLines);

    int beforeLastLine = beforeLines.size() - 1;
    int afterLastLine = afterLines.size() - 1;

    List<String> beforeComparedLines = beforeNoNewlineAtEOF ? beforeLines.subList(0, beforeLastLine) : beforeLines;
    List<String> afterComparedLines = afterNoNewlineAtEOF ? afterLines.subList(0, afterLastLine) : afterLines;
    List<Range> ranges = doCompareLines(beforeComparedLines, afterComparedLines);

    if (beforeNoNewlineAtEOF && afterNoNewlineAtEOF) {
      if (beforeLines.get(beforeLastLine).equals(afterLines.get(afterLastLine))) return ranges;
      Range range = new Range(beforeLastLine, beforeLastLine + 1, afterLastLine, afterLastLine + 1);
      return appendRange(ranges, range);
    }
    else if (beforeNoNewlineAtEOF) {
      Range range = new Range(beforeLastLine, beforeLastLine + 1, afterLastLine + 1, afterLastLine + 1);
      return appendRange(ranges, range);
    }
    else {
      Range range = new Range(beforeLastLine + 1, beforeLastLine + 1, afterLastLine, afterLastLine + 1);
      return appendRange(ranges, range);
    }
  }

  @NotNull
  private static List<Range> appendRange(@NotNull List<? extends Range> ranges, @NotNull Range change) {
    if (ranges.isEmpty()) return singletonList(change);

    Range lastRange = ranges.get(ranges.size() - 1);
    if (lastRange.end1 == change.start1 && lastRange.end2 == change.start2) {
      Range mergedChange = new Range(lastRange.start1, change.end1, lastRange.start2, change.end2);
      return ContainerUtil.append(ranges.subList(0, ranges.size() - 1), mergedChange);
    }
    else {
      return ContainerUtil.append(ranges, change);
    }
  }

  @NotNull
  private static List<Range> doCompareLines(@NotNull List<String> beforeLines, @NotNull List<String> afterLines) {
    try {
      FairDiffIterable iterable = ByLine.compare(beforeLines, afterLines, ComparisonPolicy.DEFAULT, CancellationChecker.EMPTY);
      return ContainerUtil.newArrayList(iterable.iterateChanges());
    }
    catch (DiffTooBigException e) {
      return singletonList(new Range(0, beforeLines.size(), 0, afterLines.size()));
    }
  }

  @NotNull
  private FilePatch buildBinaryPatch(@Nullable AirContentRevision beforeRevision,
                                     @Nullable AirContentRevision afterRevision) throws VcsException {
    assert beforeRevision != null || afterRevision != null;
    AirContentRevision headingBeforeRevision = beforeRevision != null ? beforeRevision : afterRevision;
    AirContentRevision headingAfterRevision = afterRevision != null ? afterRevision : beforeRevision;
    byte[] beforeContent = beforeRevision != null ? beforeRevision.getContentAsBytes() : null;
    byte[] afterContent = afterRevision != null ? afterRevision.getContentAsBytes() : null;
    BinaryFilePatch patch = new BinaryFilePatch(beforeContent, afterContent);
    setPatchHeading(patch, headingBeforeRevision, headingAfterRevision);
    return patch;
  }

  @NotNull
  private TextFilePatch buildAddedFile(@NotNull AirContentRevision afterRevision) throws VcsException {
    TextFilePatch result = buildPatchHeading(afterRevision, afterRevision);
    result.setFileStatus(FileStatus.ADDED);
    String content = getContent(afterRevision);
    if (!content.isEmpty()) {
      result.addHunk(createWholeFileHunk(content, true, false));
    }
    return result;
  }

  @NotNull
  private TextFilePatch buildDeletedFile(@NotNull AirContentRevision beforeRevision) throws VcsException {
    TextFilePatch result = buildPatchHeading(beforeRevision, beforeRevision);
    result.setFileStatus(FileStatus.DELETED);
    String content = getContent(beforeRevision);
    if (!content.isEmpty()) {
      result.addHunk(createWholeFileHunk(content, false, false));
    }
    return result;
  }

  private static void addLineToHunk(@NotNull PatchHunk hunk,
                                    @NotNull List<String> lines,
                                    @NotNull PatchLine.Type type,
                                    int index,
                                    boolean noNewlineAtEOF) {
    String line = lines.get(index);
    boolean isLastLine = index == lines.size() - 1;
    PatchLine patchLine = new PatchLine(type, line);
    patchLine.setSuppressNewLine(noNewlineAtEOF && isLastLine);
    hunk.addLine(patchLine);
  }

  @NotNull
  private static PatchHunk createWholeFileHunk(@NotNull String content, boolean isInsertion, boolean isWithEmptyFile) {
    PatchLine.Type type = isInsertion ? PatchLine.Type.ADD : PatchLine.Type.REMOVE;

    List<String> lines = tokenize(content);
    boolean noNewlineAtEOF = !content.endsWith("\n");

    int contentStart = 0;
    int contentEnd = lines.size();
    int emptyStart = isWithEmptyFile ? 0 : -1;
    int emptyEnd = isWithEmptyFile ? 0 : -1;

    PatchHunk hunk = new PatchHunk(isInsertion ? emptyStart : contentStart,
                                   isInsertion ? emptyEnd : contentEnd,
                                   isInsertion ? contentStart : emptyStart,
                                   isInsertion ? contentEnd : emptyEnd);
    for (int i = 0; i < lines.size(); i++) {
      addLineToHunk(hunk, lines, type, i, noNewlineAtEOF);
    }
    return hunk;
  }

  @NotNull
  private TextFilePatch buildPatchHeading(@NotNull AirContentRevision beforeRevision,
                                          @NotNull AirContentRevision afterRevision) {
    TextFilePatch result = new TextFilePatch(afterRevision.getCharset(), afterRevision.getLineSeparator());
    setPatchHeading(result, beforeRevision, afterRevision);
    return result;
  }

  private void setPatchHeading(@NotNull FilePatch result,
                               @NotNull AirContentRevision beforeRevision,
                               @NotNull AirContentRevision afterRevision) {
    result.setBeforeName(getRelativePath(myBasePath, beforeRevision.getPath()));
    result.setBeforeVersionId(getRevisionName(beforeRevision));

    result.setAfterName(getRelativePath(myBasePath, afterRevision.getPath()));
    result.setAfterVersionId(getRevisionName(afterRevision));
  }

  @SystemIndependent
  public static @NotNull String getRelativePath(@NotNull Path basePath, @NotNull FilePath filePath) {
    try {
      Path path = filePath.getIOFile().toPath();
      if (!path.isAbsolute()) return filePath.getPath();
      return basePath.relativize(path).toString().replace(File.separatorChar, '/');
    }
    catch (IllegalArgumentException e) {
      return filePath.getPath();
    }
  }

  @Nullable
  private static @NlsSafe String getRevisionName(@NotNull AirContentRevision revision) {
    String revisionName = revision.getRevisionNumber();
    if (!StringUtil.isEmptyOrSpaces(revisionName)) {
      return MessageFormat.format(REVISION_NAME_TEMPLATE, revisionName);
    }

    Long lastModified = revision.getLastModifiedTimestamp();
    if (lastModified != null) {
      return MessageFormat.format(DATE_NAME_TEMPLATE, Long.toString(lastModified));
    }

    return null;
  }

  @NotNull
  private static String getContent(@NotNull AirContentRevision revision) throws VcsException {
    String beforeContent = revision.getContentAsString();
    if (beforeContent == null) {
      throw new VcsException(
        VcsBundle.message("patch.failed.to.fetch.old.content.for.file.name.in.revision", revision.getPath().getPath(),
                          revision.getRevisionNumber()));
    }
    return beforeContent;
  }

  @NotNull
  private static List<String> tokenize(@NotNull String text) {
    return LineTokenizer.tokenizeIntoList(text, false, true);
  }
}
