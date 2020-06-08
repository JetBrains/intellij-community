// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.vcsUtil.VcsFileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.diff.impl.patch.PatchReader.HASH_PATTERN;
import static com.intellij.openapi.diff.impl.patch.PatchReader.PatchContentParser.stripPatchNameIfNeeded;

public final class GitPatchParser {
  @NonNls private static final String DIFF_GIT_HEADER_LINE = "diff --git";
  @NonNls private static final Pattern ourGitHeaderLinePattern = Pattern.compile(DIFF_GIT_HEADER_LINE + "\\s+(\\S+)\\s+(\\S+).*");
  @NonNls private static final Pattern ourIndexHeaderLinePattern =
    Pattern.compile("index\\s+(" + HASH_PATTERN + ")..(" + HASH_PATTERN + ").*");
  @NonNls private static final Pattern ourRenameFromPattern = Pattern.compile("\\s*rename from\\s+(\\S+)\\s*");
  @NonNls private static final Pattern ourRenameToPattern = Pattern.compile("\\s*rename to\\s+(\\S+)\\s*");
  @NonNls private static final Pattern ourFileStatusPattern = Pattern.compile("\\s*(new|deleted)\\s+file\\s+mode\\s*(\\d*)\\s*");
  @NonNls private static final Pattern ourNewFileModePattern = Pattern.compile("\\s*new\\s+mode\\s*(\\d+)\\s*");

  @NonNls private static final String ourGitBinaryContentStart = "GIT binary patch";
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
        patch = contentParser.readTextPatch(next, iterator, true);
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
      throw new PatchSyntaxException(iterator.previousIndex(), "Can't detect file names from git format header line");
    }
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
            beforeAfterName = Couple.of(fileRenameFromMatcher.group(1), fileRenameToMatcher.group(1));
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
    return new PatchInfo(beforeAfterName, sha1Indexes, parsedStatus, newFileMode);
  }

  private static void applyPatchInfo(@NotNull FilePatch patch, @NotNull GitPatchParser.PatchInfo patchInfo) {
    if (patch instanceof TextFilePatch) ((TextFilePatch)patch).setFileStatus(patchInfo.myFileStatus);

    if (patch.getBeforeName() == null) patch.setBeforeName(patchInfo.myBeforeName);
    if (patch.getAfterName() == null) patch.setAfterName(patchInfo.myAfterName);
    //remember sha-1 as version ids or set null if no info
    patch.setBeforeVersionId(patchInfo.myBeforeIndex);
    patch.setAfterVersionId(patchInfo.myAfterIndex);
    //set new file mode
    patch.setNewFileMode(patchInfo.myNewFileMode);
  }


  @Nullable
  private static Couple<String> parseNamesFromGitHeaderLine(@NotNull String start) {
    Matcher m = ourGitHeaderLinePattern.matcher(start);
    return m.matches()
           ? Couple.of(getFileNameFromGitHeaderLine(m.group(1), true),
                       getFileNameFromGitHeaderLine(m.group(2), false))
           : null;
  }

  @Nullable
  private static String getFileNameFromGitHeaderLine(@NotNull String line, boolean before) {
    return stripPatchNameIfNeeded(VcsFileUtil.unescapeGitPath(line), true, before);
  }

  @NotNull
  private static FileStatus parseFileStatus(@NotNull String status) {
    if (status.startsWith("new")) { //NON-NLS
      return FileStatus.ADDED;
    }
    else if (status.startsWith("deleted")) return FileStatus.DELETED; //NON-NLS
    return FileStatus.MODIFIED;
  }

  private static class PatchInfo {
    @Nullable private final String myBeforeName;
    @Nullable private final String myAfterName;

    @Nullable private final String myBeforeIndex;
    @Nullable private final String myAfterIndex;

    private final int myNewFileMode;

    @NotNull private final FileStatus myFileStatus;

    private PatchInfo(@NotNull Couple<String> beforeAfterName,
                      @Nullable Couple<String> indexes, @NotNull FileStatus status, int newFileMode) {
      myBeforeName = beforeAfterName.first;
      myAfterName = beforeAfterName.second;
      myBeforeIndex = Pair.getFirst(indexes);
      myAfterIndex = Pair.getSecond(indexes);
      myNewFileMode = newFileMode;
      myFileStatus = status;
    }
  }
}
