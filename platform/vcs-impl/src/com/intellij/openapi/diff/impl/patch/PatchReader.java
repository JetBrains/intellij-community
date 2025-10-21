// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.patch;

import com.google.common.collect.Iterables;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.SmartList;
import com.intellij.util.io.PathKt;
import com.intellij.vcsUtil.VcsFileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.diff.impl.patch.PatchLine.UNKNOWN_PATCH_FILE_LINE_NUMBER;
import static com.intellij.util.containers.ContainerUtil.filter;
import static com.intellij.util.containers.ContainerUtil.findAll;

public final class PatchReader {
  public static final @NonNls String NO_NEWLINE_SIGNATURE = UnifiedDiffWriter.NO_NEWLINE_SIGNATURE;
  private final List<String> myLines;
  private final PatchReader.PatchContentParser myPatchContentParser;
  private final AdditionalInfoParser myAdditionalInfoParser;
  private List<FilePatch> myPatches;
  private PatchFileHeaderInfo myPatchFileInfo;

  private enum DiffFormat {CONTEXT, UNIFIED}

  private static final @NonNls String CONTEXT_HUNK_PREFIX = "***************";
  private static final @NonNls String CONTEXT_FILE_PREFIX = "*** ";
  private static final @NonNls String UNIFIED_BEFORE_HUNK_PREFIX = "--- ";
  private static final @NonNls String UNIFIED_AFTER_HUNK_PREFIX = "+++ ";
  private static final @NonNls String DIFF_GIT_HEADER_LINE = "diff --git";
  static final @NonNls String HASH_PATTERN = "[0-9a-fA-F]+";

  private static final @NonNls Pattern ourUnifiedHunkStartPattern = Pattern.compile("@@ -(\\d+)(,(\\d+))? \\+(\\d+)(,(\\d+))? @@.*");
  private static final @NonNls Pattern ourContextBeforeHunkStartPattern = Pattern.compile("\\*\\*\\* (\\d+),(\\d+) \\*\\*\\*\\*");
  private static final @NonNls Pattern ourContextAfterHunkStartPattern = Pattern.compile("--- (\\d+),(\\d+) ----");
  private static final @NonNls Pattern ourEmptyRevisionInfoPattern = Pattern.compile("\\(\\s*revision\\s*\\)");

  private static final @NonNls Pattern ourGitHeaderLinePattern = Pattern.compile(DIFF_GIT_HEADER_LINE + "\\s+(\\S+)\\s+(\\S+).*");

  public PatchReader(CharSequence patchContent) {
    this(patchContent, true);
  }

  public PatchReader(@NotNull Path file) {
    this(PathKt.readChars(file), true);
  }

  public PatchReader(CharSequence patchContent, boolean saveHunks) {
    myLines = LineTokenizer.tokenizeIntoList(patchContent, false);
    myAdditionalInfoParser = new AdditionalInfoParser(!saveHunks);
    myPatchContentParser = new PatchContentParser(saveHunks);
  }

  public @NotNull List<TextFilePatch> readTextPatches() throws PatchSyntaxException {
    parseAllPatches();
    return getTextPatches();
  }

  public @Nullable CharSequence getBaseRevision(@NotNull String relativeFilePath) {
    final Map<String, Map<String, CharSequence>> map = myAdditionalInfoParser.getResultMap();
    if (!map.isEmpty()) {
      final Map<String, CharSequence> inner = map.get(relativeFilePath);
      if (inner != null) {
        BaseRevisionTextPatchEP baseRevisionTextPatchEP = PatchEP.EP_NAME.findExtensionOrFail(BaseRevisionTextPatchEP.class);
        return inner.get(baseRevisionTextPatchEP.getName());
      }
    }
    return null;
  }

  public @NotNull List<TextFilePatch> getTextPatches() {
    return findAll(myPatches, TextFilePatch.class);
  }

  public @NotNull List<FilePatch> getAllPatches() {
    return myPatches;
  }

  public void parseAllPatches() throws PatchSyntaxException {
    ListIterator<String> iterator = myLines.listIterator();
    if (!iterator.hasNext()) {
      myPatches = Collections.emptyList();
      throw new NotAPatchException();
    }

    String next;
    boolean containsAdditional = false;
    boolean isHeaderLine = true;
    int headerLineNum = 0;
    while (iterator.hasNext()) {
      next = iterator.next();
      final boolean containsAdditionalNow = myAdditionalInfoParser.testIsStart(next);
      if (containsAdditionalNow && containsAdditional) {
        myAdditionalInfoParser.acceptError(new PatchSyntaxException(iterator.previousIndex(), VcsBundle
          .message("patch.contains.additional.information.without.patch.itself")));
      }
      if (containsAdditionalNow) {
        isHeaderLine = false;
        containsAdditional = true;
        myAdditionalInfoParser.parse(next, iterator);
        if (!iterator.hasNext()) {
          myAdditionalInfoParser.acceptError(new PatchSyntaxException(iterator.previousIndex(), VcsBundle
            .message("patch.contains.additional.information.without.patch.itself")));
          break;
        }
        next = iterator.next();
      }

      if (myPatchContentParser.testIsStart(next)) {
        isHeaderLine = false;
        myPatchContentParser.parse(next, iterator);
        //iterator.previous();  // to correctly initialize next
        if (containsAdditional) {
          final String lastName = myPatchContentParser.getLastName();
          if (lastName == null) {
            myAdditionalInfoParser.acceptError(new PatchSyntaxException(iterator.previousIndex(), VcsBundle
              .message("patch.contains.additional.information.without.patch.itself")));
          }
          else {
            myAdditionalInfoParser.copyToResult(lastName);
          }
        }
        containsAdditional = false;
      }
      if (isHeaderLine) {
        headerLineNum++;
      }
    }
    myPatches = myPatchContentParser.getResult();
    if (myPatches.isEmpty()) {
      throw new NotAPatchException();
    }
    myPatchFileInfo = PatchFileHeaderParser.parseHeader(Iterables.limit(myLines, headerLineNum).iterator());
  }

  public @NotNull ThrowableComputable<Map<String, @Unmodifiable Map<String, CharSequence>>, PatchSyntaxException> getAdditionalInfo(@Nullable Set<String> paths) {
    PatchSyntaxException e = myAdditionalInfoParser.getSyntaxException();
    if (e != null) {
      return () -> {
        throw e;
      };
    }
    return () -> filter(myAdditionalInfoParser.getResultMap(), path -> paths == null || paths.contains(path));
  }

  public PatchFileHeaderInfo getPatchFileInfo() {
    return myPatchFileInfo;
  }

  private static final class AdditionalInfoParser implements Parser {
    // first is path!
    private final Map<String, Map<String, CharSequence>> myResultMap;
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
      if (myAddMap != null && !myAddMap.isEmpty()) {
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

      if (!iterator.hasNext()) {
        mySyntaxException = new PatchSyntaxException(iterator.previousIndex(), VcsBundle.message("patch.empty.additional.info.header"));
        return;
      }
      while (true) {
        final String header = iterator.next();
        final int idxHead = header.indexOf(UnifiedDiffWriter.ADD_INFO_HEADER);
        if (idxHead == -1) {
          if (myAddMap.isEmpty()) {
            mySyntaxException = new PatchSyntaxException(iterator.previousIndex(), VcsBundle.message("patch.empty.additional.info.header"));
          }
          iterator.previous();
          return;
        }

        final String subsystem = header.substring(idxHead + UnifiedDiffWriter.ADD_INFO_HEADER.length()).trim();
        if (!iterator.hasNext()) {
          mySyntaxException = new PatchSyntaxException(iterator.previousIndex(),
                                                       VcsBundle.message("patch.empty.0.data.section", subsystem));
          return;
        }

        final StringBuilder sb = new StringBuilder();
        myAddMap.put(subsystem, sb);
        while (iterator.hasNext()) {
          final String line = iterator.next();
          if (!line.startsWith(UnifiedDiffWriter.ADD_INFO_LINE_START)) {
            iterator.previous();
            break;
          }
          if (!sb.isEmpty()) {
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


  static final class PatchContentParser implements Parser {
    private final boolean mySaveHunks;
    private DiffFormat myDiffFormat = null;
    private final List<FilePatch> myPatches;
    private boolean myGitDiffFormat;

    PatchContentParser(boolean saveHunks) {
      mySaveHunks = saveHunks;
      myPatches = new SmartList<>();
    }

    @Override
    public boolean testIsStart(String start) {
      if (start.startsWith(DIFF_GIT_HEADER_LINE)) {
        myGitDiffFormat = true;
        return true;
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
      if (myGitDiffFormat) {
        addPatchAndResetSettings(GitPatchParser.parse(start, iterator, mySaveHunks));
      }
      else {
        addPatchAndResetSettings(readTextPatch(start, iterator));
      }
    }

    private void addPatchAndResetSettings(@Nullable FilePatch patch) {
      if (patch != null) {
        myPatches.add(patch);
      }
      myGitDiffFormat = false;
    }

    public List<FilePatch> getResult() {
      return myPatches;
    }

    TextFilePatch readTextPatch(String curLine, ListIterator<String> iterator) throws PatchSyntaxException {
      final TextFilePatch curPatch = mySaveHunks ? new TextFilePatch(null) : new EmptyTextFilePatch();
      extractFileName(curLine, curPatch, true);

      if (!iterator.hasNext()) {
        throw new PatchSyntaxException(iterator.previousIndex(),
                                       VcsBundle.message("patch.second.file.name.expected"));
      }
      curLine = iterator.next();
      String secondNamePrefix = myDiffFormat == DiffFormat.UNIFIED ? "+++ " : "--- ";
      if (!curLine.startsWith(secondNamePrefix)) {
        throw new PatchSyntaxException(iterator.previousIndex(), VcsBundle.message("patch.second.file.name.expected"));
      }
      extractFileName(curLine, curPatch, false);

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

    private static @Nullable PatchHunk readNextHunkUnified(@NotNull ListIterator<String> iterator) throws PatchSyntaxException {
      String curLine = null;
      int numIncrements = 0;
      while (iterator.hasNext()) {
        curLine = iterator.next();
        ++numIncrements;
        if (curLine.startsWith("--- ") || ourGitHeaderLinePattern.matcher(curLine).matches()) {
          for (int i = 0; i < numIncrements; i++) {
            iterator.previous();
          }
          return null;
        }
        if (curLine.startsWith("@@ ")) {
          break;
        }
      }
      if (!iterator.hasNext()) return null;

      Matcher m = ourUnifiedHunkStartPattern.matcher(curLine);
      if (!m.matches()) {
        throw new PatchSyntaxException(iterator.previousIndex(), VcsBundle.message("patch.unknown.hunk.start.syntax"));
      }
      int startLineBefore = Integer.parseInt(m.group(1));
      final String linesBeforeText = m.group(3);
      int linesBefore = linesBeforeText == null ? 1 : Integer.parseInt(linesBeforeText);
      int startLineAfter = Integer.parseInt(m.group(4));
      final String linesAfterText = m.group(6);
      int linesAfter = linesAfterText == null ? 1 : Integer.parseInt(linesAfterText);
      PatchHunk hunk = new PatchHunk(startLineBefore - 1, startLineBefore + linesBefore - 1,
                                     startLineAfter - 1, startLineAfter + linesAfter - 1);

      PatchLine lastLine = null;
      int before = 0;
      int after = 0;
      while (iterator.hasNext()) {
        String hunkCurLine = iterator.next();
        if (lastLine != null && hunkCurLine.startsWith(NO_NEWLINE_SIGNATURE)) {
          lastLine.setSuppressNewLine(true);
          continue;
        }
        lastLine = parsePatchLine(hunkCurLine, 1, before < linesBefore || after < linesAfter, iterator.previousIndex());
        if (lastLine == null) {
          iterator.previous();
          break;
        }
        switch (lastLine.getType()) {
          case CONTEXT -> {
            before++;
            after++;
          }
          case ADD -> after++;
          case REMOVE -> before++;
        }
        hunk.addLine(lastLine);
      }
      return hunk;
    }

    public @Nullable String getLastName() {
      if (myPatches.isEmpty()) {
        return null;
      }
      else {
        final FilePatch patch = myPatches.get(myPatches.size() - 1);
        return patch.getBeforeName() == null ? patch.getAfterName() : patch.getBeforeName();
      }
    }

    private static @Nullable PatchLine parsePatchLine(final String line, final int prefixLength, final int originalLineNumber) {
      return parsePatchLine(line, prefixLength, true, originalLineNumber);
    }

    private static @Nullable PatchLine parsePatchLine(final String line, final int prefixLength, boolean expectMeaningfulLines, final int originalLineNumber) {
      if (!expectMeaningfulLines) return null;

      PatchLine.Type type;
      if (line.startsWith("+")) {
        type = PatchLine.Type.ADD;
      }
      else if (line.startsWith("-")) {
        type = PatchLine.Type.REMOVE;
      }
      else if (line.isEmpty() || line.startsWith(" ")) {
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
      return new PatchLine(type, lineText, originalLineNumber);
    }

    private static @Nullable PatchHunk readNextHunkContext(ListIterator<String> iterator) throws PatchSyntaxException {
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
      if (!iterator.hasNext()) {
        return null;
      }
      Matcher beforeMatcher = ourContextBeforeHunkStartPattern.matcher(iterator.next());
      if (!beforeMatcher.matches()) {
        throw new PatchSyntaxException(iterator.previousIndex(), VcsBundle.message("patch.unknown.before.hunk.start.syntax"));
      }
      List<String> beforeLines = readContextDiffLines(iterator);
      if (!iterator.hasNext()) {
        throw new PatchSyntaxException(iterator.previousIndex(), VcsBundle.message("patch.missing.after.hunk"));
      }
      Matcher afterMatcher = ourContextAfterHunkStartPattern.matcher(iterator.next());
      if (!afterMatcher.matches()) {
        throw new PatchSyntaxException(iterator.previousIndex(), VcsBundle.message("patch.unknown.after.hunk.start.syntax"));
      }
      //if (! iterator.hasNext()) {
      //throw new PatchSyntaxException(iterator.previousIndex(), "Unexpected patch end");
      //}
      List<String> afterLines = readContextDiffLines(iterator);
      int startLineBefore = Integer.parseInt(beforeMatcher.group(1));
      int endLineBefore = Integer.parseInt(beforeMatcher.group(2));
      int startLineAfter = Integer.parseInt(afterMatcher.group(1));
      int endLineAfter = Integer.parseInt(afterMatcher.group(2));
      PatchHunk hunk = new PatchHunk(startLineBefore - 1, endLineBefore - 1, startLineAfter - 1, endLineAfter - 1);

      int beforeLineIndex = 0;
      int afterLineIndex = 0;
      PatchLine lastBeforePatchLine = null;
      PatchLine lastAfterPatchLine = null;
      if (beforeLines.isEmpty()) {
        for (String line : afterLines) {
          hunk.addLine(parsePatchLine(line, 2, UNKNOWN_PATCH_FILE_LINE_NUMBER));
        }
      }
      else if (afterLines.isEmpty()) {
        for (String line : beforeLines) {
          hunk.addLine(parsePatchLine(line, 2, UNKNOWN_PATCH_FILE_LINE_NUMBER));
        }
      }
      else {
        while (beforeLineIndex < beforeLines.size() || afterLineIndex < afterLines.size()) {
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
                   (startsWith(afterLine, " ") || afterLine == null /* handle some weird cases with line breaks truncated at EOF */)) {
            addContextDiffLine(hunk, beforeLine, PatchLine.Type.CONTEXT, UNKNOWN_PATCH_FILE_LINE_NUMBER);
            beforeLineIndex++;
            afterLineIndex++;
          }
          else if (startsWith(beforeLine, "-")) {
            lastBeforePatchLine = addContextDiffLine(hunk, beforeLine, PatchLine.Type.REMOVE, UNKNOWN_PATCH_FILE_LINE_NUMBER);
            beforeLineIndex++;
          }
          else if (startsWith(afterLine, "+")) {
            lastAfterPatchLine = addContextDiffLine(hunk, afterLine, PatchLine.Type.ADD, UNKNOWN_PATCH_FILE_LINE_NUMBER);
            afterLineIndex++;
          }
          else if (startsWith(beforeLine, "!") && startsWith(afterLine, "!")) {
            while (beforeLineIndex < beforeLines.size() && beforeLines.get(beforeLineIndex).startsWith("! ")) {
              lastBeforePatchLine = addContextDiffLine(hunk, beforeLines.get(beforeLineIndex), PatchLine.Type.REMOVE, UNKNOWN_PATCH_FILE_LINE_NUMBER);
              beforeLineIndex++;
            }

            while (afterLineIndex < afterLines.size() && afterLines.get(afterLineIndex).startsWith("! ")) {
              lastAfterPatchLine = addContextDiffLine(hunk, afterLines.get(afterLineIndex), PatchLine.Type.ADD, UNKNOWN_PATCH_FILE_LINE_NUMBER);
              afterLineIndex++;
            }
          }
          else {
            throw new PatchSyntaxException(-1, VcsBundle.message("patch.unknown.line.prefix"));
          }
        }
      }
      return hunk;
    }

    private static boolean startsWith(final @Nullable String line, final String prefix) {
      return line != null && line.startsWith(prefix);
    }

    private static PatchLine addContextDiffLine(final PatchHunk hunk, final String line, final PatchLine.Type type, final int originalLineNumber) {
      final PatchLine patchLine = new PatchLine(type, line.length() < 2 ? "" : line.substring(2), originalLineNumber);
      hunk.addLine(patchLine);
      return patchLine;
    }

    private static List<String> readContextDiffLines(ListIterator<String> iterator) {
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

    private static void extractFileName(final String curLine, final FilePatch patch, final boolean before) {
      String fileName = curLine.substring(4);
      int pos = fileName.indexOf('\t');
      if (pos < 0) {
        pos = fileName.indexOf(' ');
      }
      if (pos >= 0) {
        @NlsSafe String versionId = fileName.substring(pos).trim();
        fileName = fileName.substring(0, pos);
        if (!versionId.isEmpty() && !ourEmptyRevisionInfoPattern.matcher(versionId).matches()) {
          if (before) {
            patch.setBeforeVersionId(versionId);
          }
          else {
            patch.setAfterVersionId(versionId);
          }
        }
      }
      fileName = VcsFileUtil.unescapeGitPath(fileName);
      String newFileName = stripPatchNameIfNeeded(fileName, before);
      if (newFileName == null) return;
      if (before) {
        patch.setBeforeName(newFileName);
      }
      else {
        patch.setAfterName(newFileName);
      }
    }

    static @Nullable String stripPatchNameIfNeeded(@NotNull String fileName, boolean before) {
      if (UnifiedDiffWriter.DEV_NULL.equals(fileName)) return null;
      String prefix = before ? UnifiedDiffWriter.A_PREFIX : UnifiedDiffWriter.B_PREFIX;
      return StringUtil.trimStart(fileName, prefix);
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
