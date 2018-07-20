// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch.apply;

import com.intellij.diff.tools.util.text.LineOffsets;
import com.intellij.diff.tools.util.text.LineOffsetsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.PatchHunk;
import com.intellij.openapi.diff.impl.patch.PatchLine;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.openapi.diagnostic.Logger.getInstance;
import static com.intellij.util.ObjectUtils.notNull;

public class PlainSimplePatchApplier {
  private static final Logger LOG = getInstance(PlainSimplePatchApplier.class);

  @NotNull private final List<PatchHunk> myHunks;
  @NotNull private final CharSequence myText;
  @NotNull private final LineOffsets myLineOffsets;

  private final StringBuilder sb = new StringBuilder();
  private int baseLine = 0;
  private int patchedLine = 0;

  @Nullable
  public static String apply(@NotNull CharSequence text, @NotNull List<PatchHunk> hunks) {
    return new PlainSimplePatchApplier(text, hunks).execute();
  }

  private PlainSimplePatchApplier(@NotNull CharSequence text, @NotNull List<PatchHunk> hunks) {
    myText = text;
    myHunks = hunks;
    myLineOffsets = LineOffsetsUtil.create(text);
  }

  @Nullable
  private String execute() {
    if (myHunks.isEmpty()) return myText.toString();

    try {
      for (PatchHunk hunk : myHunks) {
        appendUnchangedLines(hunk.getStartLineBefore());

        checkContextLines(hunk);

        applyChangedLines(hunk);
      }

      if (!handleLastLine()) {
        appendUnchangedLines(myLineOffsets.getLineCount());
      }

      return sb.toString();
    }
    catch (PatchApplyException e) {
      LOG.debug(e);
      return null;
    }
  }

  private boolean handleLastLine() {
    List<PatchLine> lastHunkLines = notNull(ContainerUtil.getLastItem(myHunks)).getLines();
    PatchLine lastBaseLine = ContainerUtil.findLast(lastHunkLines, line -> line.getType() != PatchLine.Type.ADD);
    PatchLine lastPatchedLine = ContainerUtil.findLast(lastHunkLines, line -> line.getType() != PatchLine.Type.REMOVE);

    if (lastBaseLine != null) {
      boolean lastLineAlreadyApplied =
        !lastBaseLine.isSuppressNewLine() && baseLine + 1 == myLineOffsets.getLineCount() && getLineContent(baseLine).length() == 0 ||
        lastBaseLine.isSuppressNewLine() && baseLine == myLineOffsets.getLineCount();
      if (lastLineAlreadyApplied) {
        boolean isNoNewlinePatched = lastPatchedLine != null ? lastPatchedLine.isSuppressNewLine() : lastBaseLine.isSuppressNewLine();
        if (!isNoNewlinePatched) {
          if (patchedLine > 0) sb.append('\n');
        }
        return true;
      }
      return false;
    }

    // insertion into empty file - use "No newline at end of file" flag from patch
    if (baseLine == 0 && myText.length() == 0) {
      boolean isNoNewlinePatched = lastPatchedLine != null && lastPatchedLine.isSuppressNewLine();
      if (!isNoNewlinePatched) {
        if (patchedLine > 0) sb.append('\n');
      }
      return true;
    }

    return false;
  }

  private void checkContextLines(@NotNull PatchHunk hunk) {
    int baseStart = hunk.getStartLineBefore();
    int baseEnd = hunk.getEndLineBefore();
    int patchedStart = hunk.getStartLineAfter();
    int patchedEnd = hunk.getEndLineAfter();

    if (baseLine != baseStart) {
      error(String.format("Unexpected hunk base start: expected  - %s, actual - %s", baseLine, baseStart));
    }
    if (patchedLine != patchedStart) {
      error(String.format("Unexpected hunk patched start: expected  - %s, actual - %s", patchedLine, patchedStart));
    }
    if (baseEnd > myLineOffsets.getLineCount()) {
      error(String.format("Unexpected hunk base end: total lines  - %s, hunk end - %s", myLineOffsets.getLineCount(), baseEnd));
    }

    int baseCount = ContainerUtil.count(hunk.getLines(), patchLine -> patchLine.getType() != PatchLine.Type.ADD);
    int patchedCount = ContainerUtil.count(hunk.getLines(), patchLine -> patchLine.getType() != PatchLine.Type.REMOVE);

    if (baseCount != baseEnd - baseStart) {
      error(String.format("Unexpected hunk base body: expected - %s, actual - %s", baseEnd - baseStart, baseCount));
    }
    if (patchedCount != patchedEnd - patchedStart) {
      error(String.format("Unexpected hunk patched body: expected - %s, actual - %s", patchedEnd - patchedStart, patchedCount));
    }

    int count = 0;
    for (PatchLine patchLine : hunk.getLines()) {
      if (patchLine.getType() != PatchLine.Type.ADD) {
        CharSequence expectedContent = getLineContent(baseStart + count);
        String actualContent = patchLine.getText();
        if (!StringUtil.equals(expectedContent, actualContent)) {
          error(String.format("Unexpected hunk content: expected - '%s', actual - '%s'", expectedContent, patchLine));
        }
        count++;
      }
    }
  }


  private void applyChangedLines(@NotNull PatchHunk hunk) {
    for (PatchLine patchLine : hunk.getLines()) {
      if (patchLine.getType() != PatchLine.Type.REMOVE) {
        appendLine(patchLine.getText());
      }
    }
    baseLine = hunk.getEndLineBefore();
  }

  private void appendUnchangedLines(int untilLine) {
    if (baseLine > untilLine) {
      error(String.format("Unexpected base line: expected - %s, actual - %s", baseLine, untilLine));
    }
    if (untilLine > myLineOffsets.getLineCount()) {
      error(String.format("Unexpected base line: total lines - %s, actual - %s", myLineOffsets.getLineCount(), untilLine));
    }

    for (int i = baseLine; i < untilLine; i++) {
      appendLine(getLineContent(i));
    }
    baseLine = untilLine;
  }

  private void appendLine(CharSequence lineContent) {
    if (patchedLine > 0) sb.append('\n');
    sb.append(lineContent);
    patchedLine++;
  }

  @NotNull
  private CharSequence getLineContent(int line) {
    return myText.subSequence(myLineOffsets.getLineStart(line), myLineOffsets.getLineEnd(line));
  }

  private static void error(@NotNull String error) {
    throw new PatchApplyException(error);
  }

  private static class PatchApplyException extends RuntimeException {
    public PatchApplyException(String message) {
      super(message);
    }
  }
}
