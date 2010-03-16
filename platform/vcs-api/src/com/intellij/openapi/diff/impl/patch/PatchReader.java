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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 15.11.2006
 * Time: 18:05:20
 */
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.util.text.LineTokenizer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatchReader {
  @NonNls public static final String NO_NEWLINE_SIGNATURE = "\\ No newline at end of file";

  private enum DiffFormat { CONTEXT, UNIFIED }

  private final String[] myLines;
  private int myLineIndex = 0;
  private DiffFormat myDiffFormat = null;
  @NonNls private static final String CONTEXT_HUNK_PREFIX = "***************";
  @NonNls private static final String CONTEXT_FILE_PREFIX = "*** ";
  @NonNls private static final Pattern ourUnifiedHunkStartPattern = Pattern.compile("@@ -(\\d+)(,(\\d+))? \\+(\\d+)(,(\\d+))? @@.*");
  @NonNls private static final Pattern ourContextBeforeHunkStartPattern = Pattern.compile("\\*\\*\\* (\\d+),(\\d+) \\*\\*\\*\\*");
  @NonNls private static final Pattern ourContextAfterHunkStartPattern = Pattern.compile("--- (\\d+),(\\d+) ----");

  public PatchReader(CharSequence patchContent) {
    myLines = LineTokenizer.tokenize(patchContent, false);
  }

  public List<TextFilePatch> readAllPatches() throws PatchSyntaxException {
    List<TextFilePatch> result = new ArrayList<TextFilePatch>();
    while(true) {
      TextFilePatch patch = readNextPatch();
      if (patch == null) break;
      result.add(patch);
    }
    return result;
  }

  @Nullable
  public TextFilePatch readNextPatch() throws PatchSyntaxException {
    while (myLineIndex < myLines.length) {
      String curLine = myLines [myLineIndex];
      if (curLine.startsWith("--- ") && (myDiffFormat == null || myDiffFormat == DiffFormat.UNIFIED)) {
        myDiffFormat = DiffFormat.UNIFIED;
        return readPatch(curLine);
      }
      else if (curLine.startsWith(CONTEXT_FILE_PREFIX) && (myDiffFormat == null || myDiffFormat == DiffFormat.CONTEXT)) {
        myDiffFormat = DiffFormat.CONTEXT;
        return readPatch(curLine);
      }
      myLineIndex++;
    }
    return null;
  }

  private TextFilePatch readPatch(String curLine) throws PatchSyntaxException {
    final TextFilePatch curPatch;
    curPatch = new TextFilePatch();
    extractFileName(curLine, curPatch, true);
    myLineIndex++;
    curLine = myLines [myLineIndex];
    String secondNamePrefix = myDiffFormat == DiffFormat.UNIFIED ? "+++ " : "--- ";
    if (!curLine.startsWith(secondNamePrefix)) {
      throw new PatchSyntaxException(myLineIndex, "Second file name expected");
    }
    extractFileName(curLine, curPatch, false);
    myLineIndex++;
    while(myLineIndex < myLines.length) {
      PatchHunk hunk;
      if (myDiffFormat == DiffFormat.UNIFIED) {
        hunk = readNextHunkUnified();
      }
      else {
        hunk = readNextHunkContext();
      }
      if (hunk == null) break;
      curPatch.addHunk(hunk);
    }
    return curPatch;
  }

  @Nullable
  private PatchHunk readNextHunkUnified() throws PatchSyntaxException {
    while(myLineIndex < myLines.length) {
      String curLine = myLines [myLineIndex];
      if (curLine.startsWith("--- ") && myLineIndex < myLines.length-1 && myLines [myLineIndex+1].startsWith("+++ ")) {
        return null;
      }
      if (curLine.startsWith("@@ ")) {
        break;
      }
      myLineIndex++;
    }
    if (myLineIndex == myLines.length) {
      return null;
    }

    Matcher m = ourUnifiedHunkStartPattern.matcher(myLines [myLineIndex]);
    if (!m.matches()) {
      throw new PatchSyntaxException(myLineIndex, "Unknown hunk start syntax");
    }
    int startLineBefore = Integer.parseInt(m.group(1));
    final String linesBeforeText = m.group(3);
    int linesBefore = linesBeforeText == null ? 1 : Integer.parseInt(linesBeforeText);
    int startLineAfter = Integer.parseInt(m.group(4));
    final String linesAfterText = m.group(6);
    int linesAfter = linesAfterText == null ? 1 : Integer.parseInt(linesAfterText);
    PatchHunk hunk = new PatchHunk(startLineBefore-1, startLineBefore+linesBefore-1, startLineAfter-1, startLineAfter+linesAfter-1);
    myLineIndex++;
    PatchLine lastLine = null;
    while(myLineIndex < myLines.length) {
      String curLine = myLines [myLineIndex];
      if (lastLine != null && curLine.startsWith(NO_NEWLINE_SIGNATURE)) {
        lastLine.setSuppressNewLine(true);
        myLineIndex++;
        continue;
      }
      if (curLine.startsWith("--- ")) {
        break;
      }
      lastLine = parsePatchLine(curLine, 1);
      if (lastLine == null) {
        break;
      }
      hunk.addLine(lastLine);
      myLineIndex++;
    }
    return hunk;
  }

  @Nullable
  private static PatchLine parsePatchLine(final String line, final int prefixLength) {
    PatchLine.Type type;
    if (line.startsWith("+")) {
      type = PatchLine.Type.ADD;
    }
    else if (line.startsWith("-")) {
      type = PatchLine.Type.REMOVE;
    }
    else if (line.startsWith(" ")) {
      type = PatchLine.Type.CONTEXT;
    }
    else {
      return null;
    }
    String lineText;
    if (line.length() < prefixLength) {
      lineText = "";
    }
    else {
      lineText = line.substring(prefixLength); 
    }
    return new PatchLine(type, lineText);
  }

  @Nullable
  private PatchHunk readNextHunkContext() throws PatchSyntaxException {
    while(myLineIndex < myLines.length) {
      String curLine = myLines [myLineIndex];
      if (curLine.startsWith(CONTEXT_FILE_PREFIX)) {
        return null;
      }
      if (curLine.startsWith(CONTEXT_HUNK_PREFIX)) {
        break;
      }
      myLineIndex++;
    }
    if (myLineIndex == myLines.length) {
      return null;
    }
    myLineIndex++;
    Matcher beforeMatcher = ourContextBeforeHunkStartPattern.matcher(myLines [myLineIndex]);
    if (!beforeMatcher.matches()) {
      throw new PatchSyntaxException(myLineIndex, "Unknown before hunk start syntax");
    }
    myLineIndex++;
    List<String> beforeLines = readContextDiffLines();
    if (myLineIndex == myLines.length) {
      throw new PatchSyntaxException(myLineIndex, "Missing after hunk");
    }
    Matcher afterMatcher = ourContextAfterHunkStartPattern.matcher(myLines [myLineIndex]);
    if (!afterMatcher.matches()) {
      throw new PatchSyntaxException(myLineIndex, "Unknown after hunk start syntax");
    }
    myLineIndex++;
    List<String> afterLines = readContextDiffLines();
    int startLineBefore = Integer.parseInt(beforeMatcher.group(1));
    int endLineBefore = Integer.parseInt(beforeMatcher.group(2));
    int startLineAfter = Integer.parseInt(afterMatcher.group(1));
    int endLineAfter = Integer.parseInt(afterMatcher.group(2));
    PatchHunk hunk = new PatchHunk(startLineBefore-1, endLineBefore-1, startLineAfter-1, endLineAfter-1);

    int beforeLineIndex = 0;
    int afterLineIndex = 0;
    PatchLine lastBeforePatchLine = null;
    PatchLine lastAfterPatchLine = null;
    if (beforeLines.size() == 0) {
      for(String line: afterLines) {
        hunk.addLine(parsePatchLine(line, 2));
      }
    }
    else if (afterLines.size() == 0) {
      for(String line: beforeLines) {
        hunk.addLine(parsePatchLine(line, 2));
      }
    }
    else {
      while(beforeLineIndex < beforeLines.size() || afterLineIndex < afterLines.size()) {
        String beforeLine = beforeLineIndex >= beforeLines.size() ? null : beforeLines.get(beforeLineIndex);
        String afterLine = afterLineIndex >= afterLines.size() ? null : afterLines.get(afterLineIndex);
        if (startsWith(beforeLine, NO_NEWLINE_SIGNATURE) && lastBeforePatchLine != null) {
          lastBeforePatchLine.setSuppressNewLine(true);
          beforeLineIndex++;
        }
        else if (startsWith(afterLine, NO_NEWLINE_SIGNATURE) && lastAfterPatchLine != null) {
          lastAfterPatchLine.setSuppressNewLine(true);
          afterLineIndex++;
        }
        else if (startsWith(beforeLine, " ") &&
                 (startsWith(afterLine, " ") || afterLine == null /* handle some weird cases with line breaks truncated at EOF */ )) {
          addContextDiffLine(hunk, beforeLine, PatchLine.Type.CONTEXT);
          beforeLineIndex++;
          afterLineIndex++;
        }
        else if (startsWith(beforeLine, "-")) {
          lastBeforePatchLine = addContextDiffLine(hunk, beforeLine, PatchLine.Type.REMOVE);
          beforeLineIndex++;
        }
        else if (startsWith(afterLine, "+")) {
          lastAfterPatchLine = addContextDiffLine(hunk, afterLine, PatchLine.Type.ADD);
          afterLineIndex++;
        }
        else if (startsWith(beforeLine, "!") && startsWith(afterLine, "!")) {
          while(beforeLineIndex < beforeLines.size() && beforeLines.get(beforeLineIndex).startsWith("! ")) {
            lastBeforePatchLine = addContextDiffLine(hunk, beforeLines.get(beforeLineIndex), PatchLine.Type.REMOVE);
            beforeLineIndex++;
          }

          while(afterLineIndex < afterLines.size() && afterLines.get(afterLineIndex).startsWith("! ")) {
            lastAfterPatchLine = addContextDiffLine(hunk, afterLines.get(afterLineIndex), PatchLine.Type.ADD);
            afterLineIndex++;
          }
        }
        else {
          throw new PatchSyntaxException(-1, "Unknown line prefix");
        }
      }
    }
    return hunk;
  }

  private static boolean startsWith(@Nullable final String line, final String prefix) {
    return line != null && line.startsWith(prefix);
  }

  private static PatchLine addContextDiffLine(final PatchHunk hunk, final String line, final PatchLine.Type type) {
    final PatchLine patchLine = new PatchLine(type, line.length() < 2 ? "" : line.substring(2));
    hunk.addLine(patchLine);
    return patchLine;
  }

  private List<String> readContextDiffLines() {
    ArrayList<String> result = new ArrayList<String>();
    while(myLineIndex < myLines.length) {
      final String line = myLines[myLineIndex];
      if (!line.startsWith(" ") && !line.startsWith("+ ") && !line.startsWith("- ") && !line.startsWith("! ") &&
          !line.startsWith(NO_NEWLINE_SIGNATURE)) {
        break;
      }
      result.add(line);
      myLineIndex++;
    }
    return result;
  }

  private static void extractFileName(final String curLine, final FilePatch patch, final boolean before) {
    String fileName = curLine.substring(4);
    int pos = fileName.indexOf('\t');
    if (pos < 0) {
      pos = fileName.indexOf(' ');
    }
    if (pos >= 0) {
      String versionId = fileName.substring(pos).trim();
      fileName = fileName.substring(0, pos);
      if (versionId.length() > 0) {
        if (before) {
          patch.setBeforeVersionId(versionId);
        }
        else {
          patch.setAfterVersionId(versionId);
        }
      }
    }
    if (before) {
      patch.setBeforeName(fileName);
    }
    else {
      patch.setAfterName(fileName);
    }
  }
}
