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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 15.11.2006
 * Time: 18:05:20
 */
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.TransparentlyFailedValue;
import com.intellij.openapi.vcs.changes.TransparentlyFailedValueI;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatchReader {
  @NonNls public static final String NO_NEWLINE_SIGNATURE = UnifiedDiffWriter.NO_NEWLINE_SIGNATURE;
  private final List<String> myLines;
  private final PatchReader.PatchContentParser myPatchContentParser;
  private final AdditionalInfoParser myAdditionalInfoParser;
  private List<TextFilePatch> myPatches;

  private enum DiffFormat { CONTEXT, UNIFIED }

  @NonNls private static final String CONTEXT_HUNK_PREFIX = "***************";
  @NonNls private static final String CONTEXT_FILE_PREFIX = "*** ";
  @NonNls private static final String UNIFIED_BEFORE_HUNK_PREFIX = "--- ";
  @NonNls private static final String UNIFIED_AFTER_HUNK_PREFIX = "+++ ";

  @NonNls private static final Pattern ourUnifiedHunkStartPattern = Pattern.compile("@@ -(\\d+)(,(\\d+))? \\+(\\d+)(,(\\d+))? @@.*");
  @NonNls private static final Pattern ourContextBeforeHunkStartPattern = Pattern.compile("\\*\\*\\* (\\d+),(\\d+) \\*\\*\\*\\*");
  @NonNls private static final Pattern ourContextAfterHunkStartPattern = Pattern.compile("--- (\\d+),(\\d+) ----");
  @NonNls private static final Pattern ourEmptyRevisionInfoPattern = Pattern.compile("\\(\\s*revision\\s*\\)");

  public PatchReader(CharSequence patchContent) {
    this(patchContent, true);
  }

  public PatchReader(CharSequence patchContent, boolean saveHunks) {
    myLines = LineTokenizer.tokenizeIntoList(patchContent, false);
    myAdditionalInfoParser = new AdditionalInfoParser(!saveHunks);
    myPatchContentParser = new PatchContentParser(saveHunks);
  }

  public List<TextFilePatch> readAllPatches() throws PatchSyntaxException {
    parseAllPatches();
    return myPatches;
  }
  
  @Nullable
  public CharSequence getBaseRevision(final Project project, final String relativeFilePath) {
    final Map<String, Map<String, CharSequence>> map = myAdditionalInfoParser.getResultMap();
    if (! map.isEmpty()) {
      final Map<String, CharSequence> inner = map.get(relativeFilePath);
      if (inner != null) {
        final BaseRevisionTextPatchEP baseRevisionTextPatchEP = Extensions.findExtension(PatchEP.EP_NAME, project, BaseRevisionTextPatchEP.class);
        if (baseRevisionTextPatchEP != null) {
          return inner.get(baseRevisionTextPatchEP.getName());
        }
      }
    }
    return null;
  }

  /*private void callAdditionalInfoExtensions() {
    final Map<String, Map<String, CharSequence>> map = myAdditionalInfoParser.getResultMap();
    if (! map.isEmpty()) {
      PatchEP[] extensions = Extensions.getExtensions(PatchEP.EP_NAME, myProject);
      final Map<String, PatchEP> byName = new HashMap<String, PatchEP>();
      for (PatchEP extension : extensions) {
        byName.put(extension.getName(), extension);
      }
      if (extensions == null || extensions.length == 0) return;
      for (Map.Entry<String, Map<String, CharSequence>> entry : map.entrySet()) {
        final String path = entry.getKey();
        final Map<String, CharSequence> extensionToContents = entry.getValue();
        for (Map.Entry<String, CharSequence> innerEntry : extensionToContents.entrySet()) {
          final PatchEP patchEP = byName.get(innerEntry.getKey());
          if (patchEP != null) {
            patchEP.consumeContentBeforePatchApplied(path, innerEntry.getValue(), myCommitContext);
          }
        }
      }
    }
  }*/

  public List<TextFilePatch> getPatches() {
    return myPatches;
  }

  public void parseAllPatches() throws PatchSyntaxException {
    final ListIterator<String> iterator = myLines.listIterator();
    if (! iterator.hasNext()) {
      myPatches = Collections.emptyList();
      return;
    }

    String next;
    boolean containsAdditional = false;
    while (iterator.hasNext()) {
      next = iterator.next();
      final boolean containsAdditionalNow = myAdditionalInfoParser.testIsStart(next);
      if (containsAdditionalNow && containsAdditional) {
        myAdditionalInfoParser.acceptError(new PatchSyntaxException(iterator.previousIndex(), "Contains additional information without patch itself"));
      }
      if (containsAdditionalNow) {
        containsAdditional = containsAdditionalNow;
        myAdditionalInfoParser.parse(next, iterator);
        if (! iterator.hasNext()) {
          myAdditionalInfoParser.acceptError(new PatchSyntaxException(iterator.previousIndex(), "Contains additional information without patch itself"));
          break;
        }
        next = iterator.next();
      }

      if (myPatchContentParser.testIsStart(next)) {
        myPatchContentParser.parse(next, iterator);
        //iterator.previous();  // to correctly initialize next
        if (containsAdditional) {
          final String lastName = myPatchContentParser.getLastName();
          if (lastName == null) {
            myAdditionalInfoParser.acceptError(new PatchSyntaxException(iterator.previousIndex(), "Contains additional information without patch itself"));
          } else {
            myAdditionalInfoParser.copyToResult(lastName);
          }
        }
        containsAdditional = false;
      }
    }
    myPatches = myPatchContentParser.getResult();
  }

  public TransparentlyFailedValueI<Map<String, Map<String, CharSequence>>, PatchSyntaxException> getAdditionalInfo(final Set<String> filterByPaths) {
    final TransparentlyFailedValue<Map<String, Map<String, CharSequence>>, PatchSyntaxException>
      value = new TransparentlyFailedValue<>();

    final Map<String, Map<String, CharSequence>> map = myAdditionalInfoParser.getResultMap();
    final Map<String, Map<String, CharSequence>>newMap = new HashMap<>();

    for (Map.Entry<String, Map<String, CharSequence>> entry : map.entrySet()) {
      final Map<String, CharSequence> innerMap = entry.getValue();
      if (filterByPaths == null || filterByPaths.contains(entry.getKey())) {
        newMap.put(entry.getKey(), innerMap);
      }
    }
    value.set(newMap);
    final PatchSyntaxException e = myAdditionalInfoParser.getSyntaxException();
    if (e != null) {
      value.fail(e);
    }
    return value;
  }

  private static class AdditionalInfoParser implements Parser {
    // first is path!
    private final Map<String,Map<String, CharSequence>> myResultMap;
    private final boolean myIgnoreMode;
    private Map<String, CharSequence> myAddMap;
    private PatchSyntaxException mySyntaxException;

    private AdditionalInfoParser(boolean ignore) {
      myIgnoreMode = ignore;
      myAddMap = new HashMap<>();
      myResultMap = new HashMap<>();
    }

    public PatchSyntaxException getSyntaxException() {
      return mySyntaxException;
    }

    public Map<String, Map<String, CharSequence>> getResultMap() {
      return myResultMap;
    }

    public void copyToResult(final String filePath) {
      if (myAddMap != null && ! myAddMap.isEmpty()) {
        myResultMap.put(filePath, myAddMap);
        myAddMap = new HashMap<>();
      }
    }

    @Override
    public boolean testIsStart(String start) {
      if (myIgnoreMode || mySyntaxException != null) return false;  // stop on first error
      return start != null && start.contains(UnifiedDiffWriter.ADDITIONAL_PREFIX);
    }

    @Override
    public void parse(String start, ListIterator<String> iterator) {
      if (myIgnoreMode) {
        return;
      }

      if (! iterator.hasNext()) {
        mySyntaxException =  new PatchSyntaxException(iterator.previousIndex(), "Empty additional info header");
        return;
      }
      while (true) {
        final String header = iterator.next();
        final int idxHead = header.indexOf(UnifiedDiffWriter.ADD_INFO_HEADER);
        if (idxHead == -1) {
          if (myAddMap.isEmpty()) {
            mySyntaxException =  new PatchSyntaxException(iterator.previousIndex(), "Empty additional info header");
          }
          iterator.previous();
          return;
        }

        final String subsystem = header.substring(idxHead + UnifiedDiffWriter.ADD_INFO_HEADER.length()).trim();
        if (! iterator.hasNext()) {
          mySyntaxException =  new PatchSyntaxException(iterator.previousIndex(), "Empty '" + subsystem + "' data section");
          return;
        }

        final StringBuilder sb = new StringBuilder();
        myAddMap.put(subsystem, sb);
        while (iterator.hasNext()) {
          final String line = iterator.next();
          if (! line.startsWith(UnifiedDiffWriter.ADD_INFO_LINE_START)) {
            iterator.previous();
            break;
          }
          if (sb.length() > 0) {
            sb.append("\n");
          }
          sb.append(StringUtil.unescapeStringCharacters(line.substring(UnifiedDiffWriter.ADD_INFO_LINE_START.length())));
        }
      }
    }

    public void acceptError(PatchSyntaxException e) {
      mySyntaxException = e;
    }
  }


  private static class PatchContentParser implements Parser {
    private final boolean mySaveHunks;
    private DiffFormat myDiffFormat = null;
    private final List<TextFilePatch> myPatches;

    private boolean myDiffCommandLike;
    private boolean myIndexLike;

    private PatchContentParser(boolean saveHunks) {
      mySaveHunks = saveHunks;
      myPatches = new SmartList<>();
    }

    @Override
    public boolean testIsStart(String start) {
      if (start.startsWith("diff")) {
        myDiffCommandLike = true;
        return false;
      }
      if (start.startsWith("index")) {
        myIndexLike = true;
        return false;
      }

      if (start.startsWith("--- ") && (myDiffFormat == null || myDiffFormat == DiffFormat.UNIFIED)) {
        myDiffFormat = DiffFormat.UNIFIED;
        return true;
      }
      else if (start.startsWith(CONTEXT_FILE_PREFIX) && (myDiffFormat == null || myDiffFormat == DiffFormat.CONTEXT)) {
        myDiffFormat = DiffFormat.CONTEXT;
        return true;
      }
      return false;
    }

    @Override
    public void parse(String start, ListIterator<String> iterator) throws PatchSyntaxException {
      final TextFilePatch patch = readPatch(start, iterator);
      if (patch != null) {
        myPatches.add(patch);
      }
      myDiffCommandLike = false;
      myIndexLike = false;
    }

    public List<TextFilePatch> getResult() throws PatchSyntaxException {
      return myPatches;
    }

    private TextFilePatch readPatch(String curLine, ListIterator<String> iterator) throws PatchSyntaxException {
      final TextFilePatch curPatch = mySaveHunks ? new TextFilePatch(null) : new EmptyTextFilePatch();
      extractFileName(curLine, curPatch, true, myDiffCommandLike && myIndexLike);

      if (! iterator.hasNext()) throw new PatchSyntaxException(iterator.previousIndex(), "Second file name expected");
      curLine = iterator.next();
      String secondNamePrefix = myDiffFormat == DiffFormat.UNIFIED ? "+++ " : "--- ";
      if (! curLine.startsWith(secondNamePrefix)) {
        throw new PatchSyntaxException(iterator.previousIndex(), "Second file name expected");
      }
      extractFileName(curLine, curPatch, false, myDiffCommandLike && myIndexLike);

      while (iterator.hasNext()) {
        PatchHunk hunk;
        if (myDiffFormat == DiffFormat.UNIFIED) {
          hunk = readNextHunkUnified(iterator);
        }
        else {
          hunk = readNextHunkContext(iterator);
        }
        if (hunk == null) break;
        curPatch.addHunk(hunk);
      }
      if (curPatch.getBeforeName() == null) {
        curPatch.setBeforeName(curPatch.getAfterName());
      }
      if (curPatch.getAfterName() == null) {
        curPatch.setAfterName(curPatch.getBeforeName());
      }
      return curPatch;
    }

    @Nullable
    private PatchHunk readNextHunkUnified(ListIterator<String> iterator) throws PatchSyntaxException {
      String curLine = null;
      int numIncrements = 0;
      while (iterator.hasNext()) {
        curLine = iterator.next();
        ++ numIncrements;
        if (curLine.startsWith("--- ")) {
          for (int i = 0; i < numIncrements; i++) {
            iterator.previous();
          }
          return null;
        }
        if (curLine.startsWith("@@ ")) {
          break;
        }
      }
      if (! iterator.hasNext()) return null;

      Matcher m = ourUnifiedHunkStartPattern.matcher(curLine);
      if (!m.matches()) {
        throw new PatchSyntaxException(iterator.previousIndex(), "Unknown hunk start syntax");
      }
      int startLineBefore = Integer.parseInt(m.group(1));
      final String linesBeforeText = m.group(3);
      int linesBefore = linesBeforeText == null ? 1 : Integer.parseInt(linesBeforeText);
      int startLineAfter = Integer.parseInt(m.group(4));
      final String linesAfterText = m.group(6);
      int linesAfter = linesAfterText == null ? 1 : Integer.parseInt(linesAfterText);
      PatchHunk hunk = new PatchHunk(startLineBefore-1, startLineBefore+linesBefore-1, startLineAfter-1, startLineAfter+linesAfter-1);

      PatchLine lastLine = null;
      int before = 0;
      int after = 0;
      while (iterator.hasNext()) {
        String hunkCurLine = iterator.next();
        if (lastLine != null && hunkCurLine.startsWith(NO_NEWLINE_SIGNATURE)) {
          lastLine.setSuppressNewLine(true);
          continue;
        }
        lastLine = parsePatchLine(hunkCurLine, 1, before < linesBefore || after < linesAfter);
        if (lastLine == null) {
          iterator.previous();
          break;
        }
        switch (lastLine.getType()) {
          case CONTEXT:
            before++;
            after++;
            break;
          case ADD:
            after++;
            break;
          case REMOVE:
            before++;
            break;
        }
        hunk.addLine(lastLine);
      }
      return hunk;
    }

    @Nullable
    public String getLastName() {
      if (myPatches.isEmpty()) {
        return null;
      }
      else {
        final TextFilePatch patch = myPatches.get(myPatches.size() - 1);
        return patch.getBeforeName() == null ? patch.getAfterName() : patch.getBeforeName();
      }
    }

    @Nullable
    private static PatchLine parsePatchLine(final String line, final int prefixLength) {
      return parsePatchLine(line, prefixLength, true);
    }

    @Nullable
    private static PatchLine parsePatchLine(final String line, final int prefixLength, boolean expectMeaningfulLines) {
      PatchLine.Type type;
      if (line.startsWith("+") && expectMeaningfulLines) {
        type = PatchLine.Type.ADD;
      }
      else if (line.startsWith("-") && expectMeaningfulLines) {
        type = PatchLine.Type.REMOVE;
      }
      else if (line.startsWith(" ") || line.length() == 0) {
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
    private PatchHunk readNextHunkContext(ListIterator<String> iterator) throws PatchSyntaxException {
      while (iterator.hasNext()) {
        String curLine = iterator.next();
        if (curLine.startsWith(CONTEXT_FILE_PREFIX)) {
          iterator.previous();
          return null;
        }
        if (curLine.startsWith(CONTEXT_HUNK_PREFIX)) {
          break;
        }
      }
      if (! iterator.hasNext()) {
        return null;
      }
      Matcher beforeMatcher = ourContextBeforeHunkStartPattern.matcher(iterator.next());
      if (! beforeMatcher.matches()) {
        throw new PatchSyntaxException(iterator.previousIndex(), "Unknown before hunk start syntax");
      }
      List<String> beforeLines = readContextDiffLines(iterator);
      if (! iterator.hasNext()) {
        throw new PatchSyntaxException(iterator.previousIndex(), "Missing after hunk");
      }
      Matcher afterMatcher = ourContextAfterHunkStartPattern.matcher(iterator.next());
      if (! afterMatcher.matches()) {
        throw new PatchSyntaxException(iterator.previousIndex(), "Unknown after hunk start syntax");
      }
      //if (! iterator.hasNext()) {
        //throw new PatchSyntaxException(iterator.previousIndex(), "Unexpected patch end");
      //}
      List<String> afterLines = readContextDiffLines(iterator);
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

    private List<String> readContextDiffLines(ListIterator<String> iterator) {
      ArrayList<String> result = new ArrayList<>();
      while (iterator.hasNext()) {
        final String line = iterator.next();
        if (!line.startsWith(" ") && !line.startsWith("+ ") && !line.startsWith("- ") && !line.startsWith("! ") &&
            !line.startsWith(NO_NEWLINE_SIGNATURE)) {
          iterator.previous();
          break;
        }
        result.add(line);
      }
      return result;
    }

    private static void extractFileName(final String curLine, final FilePatch patch, final boolean before, final boolean gitPatch) {
      String fileName = curLine.substring(4);
      int pos = fileName.indexOf('\t');
      if (pos < 0) {
        pos = fileName.indexOf(' ');
      }
      if (pos >= 0) {
        String versionId = fileName.substring(pos).trim();
        fileName = fileName.substring(0, pos);
        if (versionId.length() > 0 && !ourEmptyRevisionInfoPattern.matcher(versionId).matches()) {
          if (before) {
            patch.setBeforeVersionId(versionId);
          }
          else {
            patch.setAfterVersionId(versionId);
          }
        }
      }
      if ("/dev/null".equals(fileName)) return;
      if (before) {
        if (gitPatch && fileName.startsWith("a/")) {
          fileName = fileName.substring(2);
        }
        patch.setBeforeName(fileName);
      }
      else {
        if (gitPatch && fileName.startsWith("b/")) {
          fileName = fileName.substring(2);
        }
        patch.setAfterName(fileName);
      }
    }
  }

  private interface Parser {
    boolean testIsStart(final String start);
    void parse(final String start, final ListIterator<String> iterator) throws PatchSyntaxException;
  }

  private static class EmptyTextFilePatch extends TextFilePatch {
    private int myHunkCount = 0;
    private boolean myNew;
    private boolean myDeleted;

    EmptyTextFilePatch() {
      super(null);
    }

    @Override
    public void addHunk(PatchHunk hunk) {
      if (myHunkCount == 0) {
        if (hunk.isNewContent()) {
          myNew = true;
        }
        else if (hunk.isDeletedContent()) {
          myDeleted = true;
        }
      }
      myHunkCount++;
    }

    @Override
    public boolean isNewFile() {
      return myHunkCount == 1 && myNew;
    }

    @Override
    public boolean isDeletedFile() {
      return myHunkCount == 1 && myDeleted;
    }
  }

  public static boolean isPatchContent(@Nullable String content) {
    if (content == null) return false;
    List<String> lines = LineTokenizer.tokenizeIntoList(content, false);
    final ListIterator<String> iterator = lines.listIterator();
    DiffFormat currentFormat = null;
    while (iterator.hasNext()) {
      String line = iterator.next();
      if (line.startsWith(CONTEXT_HUNK_PREFIX)) {
        currentFormat = DiffFormat.CONTEXT;
      }
      else if (currentFormat == DiffFormat.CONTEXT && ourContextBeforeHunkStartPattern.matcher(line).matches()) {
        break;
      }
      else if (line.startsWith(UNIFIED_BEFORE_HUNK_PREFIX) && currentFormat == null) {
        currentFormat = DiffFormat.UNIFIED;
      }
      else if (currentFormat == DiffFormat.UNIFIED && line.startsWith(UNIFIED_AFTER_HUNK_PREFIX)) break;
    }
    if (currentFormat == null) return false; // can't detect format
    // check that contains at least one chunk
    while (iterator.hasNext()) {
      String line = iterator.next();
      if (currentFormat == DiffFormat.CONTEXT) {
        if (ourContextAfterHunkStartPattern.matcher(line).matches()) return true;
      }
      else {
        if (ourUnifiedHunkStartPattern.matcher(line).matches()) return true;
      }
    }
    return false;
  }
}
