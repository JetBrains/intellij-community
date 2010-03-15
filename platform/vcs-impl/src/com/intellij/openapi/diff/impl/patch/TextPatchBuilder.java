/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.BinaryContentRevision;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * @author yole
 */
public class TextPatchBuilder {
  private static final int CONTEXT_LINES = 3;
  @NonNls private static final String REVISION_NAME_TEMPLATE = "(revision {0})";

  private TextPatchBuilder() {
  }

  private static void checkCanceled(final ProgressIndicator ind) {
    if (ind != null && ind.isCanceled()) {
      throw new ProcessCanceledException();
    }
  }

  public static List<FilePatch> buildPatch(final Collection<Change> changes, final String basePath, final boolean reversePatch) throws VcsException {
    final ProgressIndicator ind = ProgressManager.getInstance().getProgressIndicator();
    
    List<FilePatch> result = new ArrayList<FilePatch>();
    for(Change c: changes) {
      checkCanceled(ind);

      final ContentRevision beforeRevision;
      final ContentRevision afterRevision;
      if (reversePatch) {
        beforeRevision = c.getAfterRevision();
        afterRevision = c.getBeforeRevision();
      }
      else {
        beforeRevision = c.getBeforeRevision();
        afterRevision = c.getAfterRevision();
      }
      if (beforeRevision != null && beforeRevision.getFile().isDirectory()) {
        continue;
      }
      if (afterRevision != null && afterRevision.getFile().isDirectory()) {
        continue;
      }

      if (beforeRevision instanceof BinaryContentRevision || afterRevision instanceof BinaryContentRevision) {
        result.add(buildBinaryPatch(basePath, (BinaryContentRevision) beforeRevision, (BinaryContentRevision) afterRevision));
        continue;
      }

      if (beforeRevision == null) {
        result.add(buildAddedFile(basePath, afterRevision, ind));
        continue;
      }
      if (afterRevision == null) {
        result.add(buildDeletedFile(basePath, beforeRevision, ind));
        continue;
      }

      final String beforeContent = beforeRevision.getContent();
      if (beforeContent == null) {
        throw new VcsException("Failed to fetch old content for changed file " + beforeRevision.getFile().getPath());
      }
      final String afterContent = afterRevision.getContent();
      if (afterContent == null) {
        throw new VcsException("Failed to fetch new content for changed file " + afterRevision.getFile().getPath());
      }
      String[] beforeLines = new LineTokenizer(beforeContent).execute();
      String[] afterLines = new LineTokenizer(afterContent).execute();

      DiffFragment[] woFormattingBlocks = DiffPolicy.LINES_WO_FORMATTING.buildFragments(beforeContent, afterContent);
      DiffFragment[] step1lineFragments = new DiffCorrection.TrueLineBlocks(ComparisonPolicy.DEFAULT).correctAndNormalize(woFormattingBlocks);
      ArrayList<LineFragment> fragments = new DiffFragmentsProcessor().process(step1lineFragments);

      if (fragments.size() > 1 || (fragments.size() == 1 && fragments.get(0).getType() != null && fragments.get(0).getType() != TextDiffTypeEnum.NONE)) {
        TextFilePatch patch = buildPatchHeading(basePath, beforeRevision, afterRevision);
        result.add(patch);

        int lastLine1 = 0;
        int lastLine2 = 0;

        while(fragments.size() > 0) {
          checkCanceled(ind);

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
              checkCanceled(ind);
              
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
      } else if (! beforeRevision.getFile().equals(afterRevision.getFile())) {
        result.add(buildMovedFile(basePath, beforeRevision, afterRevision, beforeLines));
      }
    }
    return result;
  }



  private static FilePatch buildBinaryPatch(final String basePath,
                                            final BinaryContentRevision beforeRevision,
                                            final BinaryContentRevision afterRevision) throws VcsException {
    ContentRevision headingBeforeRevision = beforeRevision != null ? beforeRevision : afterRevision;
    ContentRevision headingAfterRevision = afterRevision != null ? afterRevision : beforeRevision;
    byte[] beforeContent = beforeRevision != null ? beforeRevision.getBinaryContent() : null;
    byte[] afterContent = afterRevision != null ? afterRevision.getBinaryContent() : null;
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

  private static TextFilePatch buildMovedFile(final String basePath, final ContentRevision beforeRevision,
                                              final ContentRevision afterRevision, final String[] lines) throws VcsException {
    final TextFilePatch result = buildPatchHeading(basePath, beforeRevision, afterRevision);
    final PatchHunk hunk = new PatchHunk(0, 0, 0, 0);
    result.addHunk(hunk);
    return result;
  }

  private static TextFilePatch buildAddedFile(final String basePath, final ContentRevision afterRevision, final ProgressIndicator ind) throws VcsException {
    final String content = afterRevision.getContent();
    if (content == null) {
      throw new VcsException("Failed to fetch content for added file " + afterRevision.getFile().getPath());
    }
    String[] lines = new LineTokenizer(content).execute();
    TextFilePatch result = buildPatchHeading(basePath, afterRevision, afterRevision);
    PatchHunk hunk = new PatchHunk(-1, -1, 0, lines.length);
    for(String line: lines) {
      checkCanceled(ind);
      addLineToHunk(hunk, line, PatchLine.Type.ADD);
    }
    result.addHunk(hunk);
    return result;
  }

  private static TextFilePatch buildDeletedFile(String basePath, ContentRevision beforeRevision, final ProgressIndicator ind) throws VcsException {
    final String content = beforeRevision.getContent();
    if (content == null) {
      throw new VcsException("Failed to fetch old content for deleted file " + beforeRevision.getFile().getPath());
    }
    String[] lines = new LineTokenizer(content).execute();
    TextFilePatch result = buildPatchHeading(basePath, beforeRevision, beforeRevision);
    PatchHunk hunk = new PatchHunk(0, lines.length, -1, -1);
    for(String line: lines) {
      checkCanceled(ind);
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

  private static String getRelativePath(final String basePath, final File ioFile) {
    String relPath = FileUtil.getRelativePath(new File(basePath), ioFile);
    if (relPath == null) relPath = ioFile.getPath();
    return relPath.replace(File.separatorChar, '/');
  }

  private static String getRevisionName(final ContentRevision revision, final File ioFile) {
    String revisionName = revision.getRevisionNumber().asString();
    if (revisionName.length() > 0) {
      return MessageFormat.format(REVISION_NAME_TEMPLATE, revisionName);
    }
    return new Date(ioFile.lastModified()).toString();
  }

  private static TextFilePatch buildPatchHeading(final String basePath, final ContentRevision beforeRevision, final ContentRevision afterRevision) {
    TextFilePatch result = new TextFilePatch();
    setPatchHeading(result, basePath, beforeRevision, afterRevision);
    return result;
  }

  private static void setPatchHeading(final FilePatch result, final String basePath,
                                      @NotNull final ContentRevision beforeRevision,
                                      @NotNull final ContentRevision afterRevision) {
    File beforeFile = beforeRevision.getFile().getIOFile();
    result.setBeforeName(getRelativePath(basePath, beforeFile));
    result.setBeforeVersionId(getRevisionName(beforeRevision, beforeFile));

    File afterFile = afterRevision.getFile().getIOFile();
    result.setAfterName(getRelativePath(basePath, afterFile));
    result.setAfterVersionId(getRevisionName(afterRevision, afterFile));
  }
}
