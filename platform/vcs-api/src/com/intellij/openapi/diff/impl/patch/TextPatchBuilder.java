/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.diff.LineTokenizer;
import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.fragments.LineFragment;
import com.intellij.openapi.diff.impl.processing.DiffCorrection;
import com.intellij.openapi.diff.impl.processing.DiffFragmentsProcessor;
import com.intellij.openapi.diff.impl.processing.DiffPolicy;
import com.intellij.openapi.diff.impl.util.TextDiffTypeEnum;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.BeforeAfter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class TextPatchBuilder {
  private static final int CONTEXT_LINES = 3;
  @NonNls private static final String REVISION_NAME_TEMPLATE = "(revision {0})";
  @NonNls private static final String DATE_NAME_TEMPLATE = "(date {0})";

  private final String myBasePath;
  private final boolean myIsReversePath;
  private final boolean myIsCaseSensitive;
  @Nullable
  private final Runnable myCancelChecker;

  private TextPatchBuilder(final String basePath, final boolean isReversePath, final boolean isCaseSensitive,
                           @Nullable final Runnable cancelChecker) {
    myBasePath = basePath;
    myIsReversePath = isReversePath;
    myIsCaseSensitive = isCaseSensitive;
    myCancelChecker = cancelChecker;
  }

  private void checkCanceled() {
    if (myCancelChecker != null) {
      myCancelChecker.run();
    }
  }

  public static List<FilePatch> buildPatch(final Collection<BeforeAfter<AirContentRevision>> changes, final String basePath,
                   final boolean reversePatch, final boolean isCaseSensitive, @Nullable final Runnable cancelChecker) throws VcsException {
    final TextPatchBuilder builder = new TextPatchBuilder(basePath, reversePatch, isCaseSensitive, cancelChecker);
    return builder.build(changes);
  }

  private List<FilePatch> build(final Collection<BeforeAfter<AirContentRevision>> changes) throws VcsException {
    List<FilePatch> result = new ArrayList<FilePatch>();
    for(BeforeAfter<AirContentRevision> c: changes) {
      checkCanceled();

      final AirContentRevision beforeRevision;
      final AirContentRevision afterRevision;
      if (myIsReversePath) {
        beforeRevision = c.getAfter();
        afterRevision = c.getBefore();
      }
      else {
        beforeRevision = c.getBefore();
        afterRevision = c.getAfter();
      }
      if (beforeRevision != null && beforeRevision.getPath().isDirectory()) {
        continue;
      }
      if (afterRevision != null && afterRevision.getPath().isDirectory()) {
        continue;
      }

      if ((beforeRevision != null) && beforeRevision.isBinary() || (afterRevision != null) && afterRevision.isBinary()) {
        result.add(buildBinaryPatch(myBasePath, beforeRevision, afterRevision));
        continue;
      }

      if (beforeRevision == null) {
        result.add(buildAddedFile(myBasePath, afterRevision));
        continue;
      }
      if (afterRevision == null) {
        result.add(buildDeletedFile(myBasePath, beforeRevision));
        continue;
      }

      final String beforeContent = beforeRevision.getContentAsString();
      if (beforeContent == null) {
        throw new VcsException("Failed to fetch old content for changed file " + beforeRevision.getPath().getPath());
      }
      final String afterContent = afterRevision.getContentAsString();
      if (afterContent == null) {
        throw new VcsException("Failed to fetch new content for changed file " + afterRevision.getPath().getPath());
      }
      String[] beforeLines = new LineTokenizer(beforeContent).execute();
      String[] afterLines = new LineTokenizer(afterContent).execute();

      DiffFragment[] woFormattingBlocks = DiffPolicy.LINES_WO_FORMATTING.buildFragments(beforeContent, afterContent);
      DiffFragment[] step1lineFragments = new DiffCorrection.TrueLineBlocks(ComparisonPolicy.DEFAULT).correctAndNormalize(woFormattingBlocks);
      ArrayList<LineFragment> fragments = new DiffFragmentsProcessor().process(step1lineFragments);

      if (fragments.size() > 1 || (fragments.size() == 1 && fragments.get(0).getType() != null && fragments.get(0).getType() != TextDiffTypeEnum.NONE)) {
        TextFilePatch patch = buildPatchHeading(myBasePath, beforeRevision, afterRevision);
        result.add(patch);

        int lastLine1 = 0;
        int lastLine2 = 0;

        while(fragments.size() > 0) {
          checkCanceled();

          List<LineFragment> adjacentFragments = getAdjacentFragments(fragments);
          if (adjacentFragments.size() > 0) {
            LineFragment first = adjacentFragments.get(0);
            LineFragment last = adjacentFragments.get(adjacentFragments.size()-1);

            final int start1 = first.getStartingLine1();
            final int start2 = first.getStartingLine2();
            final int end1 = last.getStartingLine1() + last.getModifiedLines1();
            final int end2 = last.getStartingLine2() + last.getModifiedLines2();
            int contextStart1 = Math.max(start1 - CONTEXT_LINES, lastLine1);
            int contextStart2 = Math.max(start2 - CONTEXT_LINES, lastLine2);
            int contextEnd1 = Math.min(end1 + CONTEXT_LINES, beforeLines.length);
            int contextEnd2 = Math.min(end2 + CONTEXT_LINES, afterLines.length);

            PatchHunk hunk = new PatchHunk(contextStart1, contextEnd1, contextStart2, contextEnd2);
            patch.addHunk(hunk);

            for(LineFragment fragment: adjacentFragments) {
              checkCanceled();
              
              for(int i=contextStart1; i<fragment.getStartingLine1(); i++) {
                addLineToHunk(hunk, beforeLines [i], PatchLine.Type.CONTEXT);
              }
              for(int i=fragment.getStartingLine1(); i<fragment.getStartingLine1()+fragment.getModifiedLines1(); i++) {
                addLineToHunk(hunk, beforeLines [i], PatchLine.Type.REMOVE);
              }
              for(int i=fragment.getStartingLine2(); i<fragment.getStartingLine2()+fragment.getModifiedLines2(); i++) {
                addLineToHunk(hunk, afterLines[i], PatchLine.Type.ADD);
              }
              contextStart1 = fragment.getStartingLine1()+fragment.getModifiedLines1();
            }
            for(int i=contextStart1; i<contextEnd1; i++) {
              addLineToHunk(hunk, beforeLines [i], PatchLine.Type.CONTEXT);
            }
          }
        }
      } else if (! beforeRevision.getPath().equals(afterRevision.getPath())) {
        result.add(buildMovedFile(myBasePath, beforeRevision, afterRevision, beforeLines));
      }
    }
    return result;
  }

  private FilePatch buildBinaryPatch(final String basePath,
                                            final AirContentRevision beforeRevision,
                                            final AirContentRevision afterRevision) throws VcsException {
    AirContentRevision headingBeforeRevision = beforeRevision != null ? beforeRevision : afterRevision;
    AirContentRevision headingAfterRevision = afterRevision != null ? afterRevision : beforeRevision;
    byte[] beforeContent = beforeRevision != null ? beforeRevision.getContentAsBytes() : null;
    byte[] afterContent = afterRevision != null ? afterRevision.getContentAsBytes() : null;
    BinaryFilePatch patch = new BinaryFilePatch(beforeContent, afterContent);
    setPatchHeading(patch, basePath, headingBeforeRevision, headingAfterRevision);
    return patch;
  }

  private static void addLineToHunk(final PatchHunk hunk, final String line, final PatchLine.Type type) {
    final PatchLine patchLine;
    if (!line.endsWith("\n")) {
      patchLine = new PatchLine(type, line);
      patchLine.setSuppressNewLine(true);
    }
    else {
      patchLine = new PatchLine(type, line.substring(0, line.length()-1));
    }
    hunk.addLine(patchLine);
  }

  private TextFilePatch buildMovedFile(final String basePath, final AirContentRevision beforeRevision,
                                              final AirContentRevision afterRevision, final String[] lines) throws VcsException {
    final TextFilePatch result = buildPatchHeading(basePath, beforeRevision, afterRevision);
    final PatchHunk hunk = new PatchHunk(0, 0, 0, 0);
    result.addHunk(hunk);
    return result;
  }

  private TextFilePatch buildAddedFile(final String basePath, final AirContentRevision afterRevision) throws VcsException {
    final String content = afterRevision.getContentAsString();
    if (content == null) {
      throw new VcsException("Failed to fetch content for added file " + afterRevision.getPath().getPath());
    }
    String[] lines = new LineTokenizer(content).execute();
    TextFilePatch result = buildPatchHeading(basePath, afterRevision, afterRevision);
    PatchHunk hunk = new PatchHunk(-1, -1, 0, lines.length);
    for(String line: lines) {
      checkCanceled();
      addLineToHunk(hunk, line, PatchLine.Type.ADD);
    }
    result.addHunk(hunk);
    return result;
  }

  private TextFilePatch buildDeletedFile(String basePath, AirContentRevision beforeRevision) throws VcsException {
    final String content = beforeRevision.getContentAsString();
    if (content == null) {
      throw new VcsException("Failed to fetch old content for deleted file " + beforeRevision.getPath().getPath());
    }
    String[] lines = new LineTokenizer(content).execute();
    TextFilePatch result = buildPatchHeading(basePath, beforeRevision, beforeRevision);
    PatchHunk hunk = new PatchHunk(0, lines.length, -1, -1);
    for(String line: lines) {
      checkCanceled();
      addLineToHunk(hunk, line, PatchLine.Type.REMOVE);
    }
    result.addHunk(hunk);
    return result;
  }

  private static List<LineFragment> getAdjacentFragments(final ArrayList<LineFragment> fragments) {
    List<LineFragment> result = new ArrayList<LineFragment>();
    int endLine = -1;
    while(!fragments.isEmpty()) {
      LineFragment fragment = fragments.get(0);
      if (fragment.getType() == null || fragment.getType() == TextDiffTypeEnum.NONE) {
        fragments.remove(0);
        continue;
      }

      if (result.isEmpty() || endLine + CONTEXT_LINES >= fragment.getStartingLine1() - CONTEXT_LINES) {
        result.add(fragment);
        fragments.remove(0);
        endLine = fragment.getStartingLine1() + fragment.getModifiedLines1();
      }
      else {
        break;
      }
    }
    return result;
  }

  private String getRelativePath(final String basePath, final String secondPath) {
    final String baseModified = FileUtil.toSystemIndependentName(basePath);
    final String secondModified = FileUtil.toSystemIndependentName(secondPath);
    
    final String relPath = FileUtil.getRelativePath(baseModified, secondModified, '/', myIsCaseSensitive);
    if (relPath == null) return secondModified;
    return relPath;
  }

  private static String getRevisionName(final AirContentRevision revision) {
    final String revisionName = revision.getRevisionNumber();
    if (revisionName != null) {
      return MessageFormat.format(REVISION_NAME_TEMPLATE, revisionName);
    }
    return MessageFormat.format(DATE_NAME_TEMPLATE, Long.toString(revision.getPath().lastModified()));
  }

  private TextFilePatch buildPatchHeading(final String basePath, final AirContentRevision beforeRevision, final AirContentRevision afterRevision) {
    TextFilePatch result = new TextFilePatch();
    setPatchHeading(result, basePath, beforeRevision, afterRevision);
    return result;
  }

  private void setPatchHeading(final FilePatch result, final String basePath,
                                      @NotNull final AirContentRevision beforeRevision,
                                      @NotNull final AirContentRevision afterRevision) {
    result.setBeforeName(getRelativePath(basePath, beforeRevision.getPath().getPath()));
    result.setBeforeVersionId(getRevisionName(beforeRevision));

    result.setAfterName(getRelativePath(basePath, afterRevision.getPath().getPath()));
    result.setAfterVersionId(getRevisionName(afterRevision));
  }
}
