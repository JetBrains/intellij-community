// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.vcsUtil.VcsFileUtil;
import org.jetbrains.annotations.*;

import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.diff.impl.patch.PatchReader.HASH_PATTERN;
import static com.intellij.openapi.diff.impl.patch.PatchReader.PatchContentParser.stripPatchNameIfNeeded;

@ApiStatus.Internal
public final class GitPatchParser {
  private static final @NonNls String DIFF_GIT_HEADER_LINE = "diff --git";
  private static final @NonNls Pattern SIMPLE_HEADER_PATTERN = Pattern.compile(DIFF_GIT_HEADER_LINE + "\\s+(\\S+)\\s+(\\S+).*"); // NB: can't handle whitespaces in file names
  private static final @NonNls Pattern AB_PREFIX_HEADER_PATTERN = Pattern.compile(DIFF_GIT_HEADER_LINE + " a/(.+) b/(.+)");
  private static final @NonNls Pattern QUOTED_AB_PREFIX_HEADER_PATTERN = Pattern.compile(DIFF_GIT_HEADER_LINE + " \"a/(.+)\" \"b/(.+)\"\\s*");
  private static final @NonNls Pattern ourIndexHeaderLinePattern =
    Pattern.compile("index\\s+(" + HASH_PATTERN + ")..(" + HASH_PATTERN + ").*");
  private static final @NonNls Pattern ourRenameFromPattern = Pattern.compile("\\s*rename from\\s(.*)");
  private static final @NonNls Pattern ourRenameToPattern = Pattern.compile("\\s*rename to\\s(.*)");
  private static final @NonNls Pattern ourFileStatusPattern = Pattern.compile("\\s*(new|deleted)\\s+file\\s+mode\\s*(\\d*)\\s*");
  private static final @NonNls Pattern ourNewFileModePattern = Pattern.compile("\\s*new\\s+mode\\s*(\\d+)\\s*");

  private static final @NonNls String ourGitBinaryContentStart = "GIT binary patch";
  private static final Logger LOG = Logger.getInstance(GitPatchParser.class);


  public static FilePatch parse(@NotNull String start, @NotNull ListIterator<String> iterator, boolean saveHunks)
    throws PatchSyntaxException {
    FilePatch patch = null;
    PatchReader.PatchContentParser contentParser = new PatchReader.PatchContentParser(saveHunks);
    PatchInfo patchInfo = parseGitHeader(start, iterator, contentParser);
    if (iterator.hasNext()) {
      String next = iterator.next();
      if (next.startsWith(ourGitBinaryContentStart)) {
        patch = BinaryPatchContentParser.readGitBinaryFormatPatch(iterator, patchInfo.myFileStatus);
      }
      else if (next.startsWith(DIFF_GIT_HEADER_LINE)) {
        // next filePatch started
        patch = new TextFilePatch(null);
        iterator.previous();
      }
      else if (contentParser.testIsStart(next)) {
        patch = contentParser.readTextPatch(next, iterator);
      }
    }
    if (patch == null) {
      patch = new TextFilePatch(null);
      //maybe an exception should be thrown!
    }
    applyPatchInfo(patch, patchInfo);
    return patch;
  }

  private static PatchInfo parseGitHeader(@NotNull String startLine,
                                          @NotNull ListIterator<String> iterator,
                                          @NotNull PatchReader.PatchContentParser contentParser)
    throws PatchSyntaxException {
    Couple<String> beforeAfterName = parseNamesFromGitHeaderLine(startLine);
    FileStatus parsedStatus = FileStatus.MODIFIED;
    int newFileMode = -1;
    Couple<String> sha1Indexes = null;
    if (beforeAfterName == null) {
      throw new PatchSyntaxException(iterator.previousIndex(),
                                     VcsBundle.message("patch.can.t.detect.file.names.from.git.format.header.line"));
    }
    boolean preferPatchInfoPaths = false;
    while (iterator.hasNext()) {
      String next = iterator.next();
      Matcher indexMatcher = ourIndexHeaderLinePattern.matcher(next);
      Matcher fileStatusMatcher = ourFileStatusPattern.matcher(next);
      Matcher fileModeMatcher = ourNewFileModePattern.matcher(next);
      Matcher fileRenameFromMatcher = ourRenameFromPattern.matcher(next);
      try {
        if (fileStatusMatcher.matches()) {
          parsedStatus = parseFileStatus(fileStatusMatcher.group(1));
          String additionalModeInfo = fileStatusMatcher.group(2);
          if (!StringUtil.isEmptyOrSpaces(additionalModeInfo) && parsedStatus == FileStatus.ADDED) {
            newFileMode = Integer.parseInt(additionalModeInfo);
          }
        }
        else if (fileModeMatcher.matches()) {
          newFileMode = Integer.parseInt(fileModeMatcher.group(1));
        }
        else if (indexMatcher.matches()) {
          sha1Indexes = Couple.of(indexMatcher.group(1), indexMatcher.group(2));
        }
        else if (fileRenameFromMatcher.matches() && iterator.hasNext()) {
          Matcher fileRenameToMatcher = ourRenameToPattern.matcher(iterator.next());
          if (fileRenameToMatcher.matches()) {
            beforeAfterName = Couple.of(VcsFileUtil.unescapeGitPath(fileRenameFromMatcher.group(1).trim()),
                                        VcsFileUtil.unescapeGitPath(fileRenameToMatcher.group(1).trim()));
            preferPatchInfoPaths = true; // this form has non-ambiguous parsing, prefer it to other sources
          }
          else {
            iterator.previous();
          }
        }
        else if (contentParser.testIsStart(next) || next.startsWith(ourGitBinaryContentStart)) {
          iterator.previous();
          break;
        }
      }
      catch (NumberFormatException e) {
        LOG.debug("Can't parse file mode from " + next);
      }
    }
    return new PatchInfo(beforeAfterName, preferPatchInfoPaths, sha1Indexes, parsedStatus, newFileMode);
  }

  private static void applyPatchInfo(@NotNull FilePatch patch, @NotNull GitPatchParser.PatchInfo patchInfo) {
    if (patch instanceof TextFilePatch) ((TextFilePatch)patch).setFileStatus(patchInfo.myFileStatus);

    // 'patch.getBeforeName() | getAfterName()' values pre-filled from '--- a/1.txt | +++ b/1.txt' lines
    if (patch.getBeforeName() == null || patchInfo.myPreferPatchInfoPaths) patch.setBeforeName(patchInfo.myBeforeName);
    if (patch.getAfterName() == null || patchInfo.myPreferPatchInfoPaths) patch.setAfterName(patchInfo.myAfterName);
    //remember sha-1 as version ids, but keep '(revision <hash>)' suffixes from TextPatchBuilder.REVISION_NAME_TEMPLATE
    if (patchInfo.myBeforeIndex != null || patchInfo.myAfterIndex != null) {
      patch.setBeforeVersionId(patchInfo.myBeforeIndex);
      patch.setAfterVersionId(patchInfo.myAfterIndex);
    }
    //set new file mode
    patch.setNewFileMode(patchInfo.myNewFileMode);
  }


  private static @Nullable Couple<String> parseNamesFromGitHeaderLine(@NotNull String start) {
    Matcher m = AB_PREFIX_HEADER_PATTERN.matcher(start);
    if (m.matches()) {
      return getFileNamesFromGitHeaderLine(m.group(1), m.group(2));
    }

    m = QUOTED_AB_PREFIX_HEADER_PATTERN.matcher(start);
    if (m.matches()) {
      return getFileNamesFromGitHeaderLine(m.group(1), m.group(2));
    }

    m = SIMPLE_HEADER_PATTERN.matcher(start);
    if (m.matches()) {
      return getFileNamesFromGitHeaderLine(m.group(1), m.group(2));
    }

    return null;
  }

  private static @NotNull Couple<String> getFileNamesFromGitHeaderLine(@NotNull String path1, @NotNull String path2) {
    return Couple.of(getFileNameFromGitHeaderLine(path1, true),
                     getFileNameFromGitHeaderLine(path2, false));
  }

  private static @Nullable String getFileNameFromGitHeaderLine(@NotNull String line, boolean before) {
    return stripPatchNameIfNeeded(VcsFileUtil.unescapeGitPath(line), before);
  }

  private static @NotNull FileStatus parseFileStatus(@NotNull String status) {
    if (status.startsWith("new")) { //NON-NLS
      return FileStatus.ADDED;
    }
    else if (status.startsWith("deleted")) return FileStatus.DELETED; //NON-NLS
    return FileStatus.MODIFIED;
  }

  private static final class PatchInfo {
    private final @Nullable String myBeforeName;
    private final @Nullable String myAfterName;
    private final boolean myPreferPatchInfoPaths;

    private final @Nullable @Nls String myBeforeIndex;
    private final @Nullable @Nls String myAfterIndex;

    private final int myNewFileMode;

    private final @NotNull FileStatus myFileStatus;

    private PatchInfo(@NotNull Couple<String> beforeAfterName,
                      boolean preferPatchInfoPaths,
                      @Nullable Couple<@Nls String> indexes,
                      @NotNull FileStatus status,
                      int newFileMode) {
      myBeforeName = beforeAfterName.first;
      myAfterName = beforeAfterName.second;
      myPreferPatchInfoPaths = preferPatchInfoPaths;
      myBeforeIndex = Pair.getFirst(indexes);
      myAfterIndex = Pair.getSecond(indexes);
      myNewFileMode = newFileMode;
      myFileStatus = status;
    }
  }
}
