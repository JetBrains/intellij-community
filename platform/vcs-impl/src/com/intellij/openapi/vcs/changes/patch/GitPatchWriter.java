// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.intellij.openapi.diff.impl.patch.PatchUtil.EXECUTABLE_FILE_MODE;
import static com.intellij.openapi.diff.impl.patch.PatchUtil.REGULAR_FILE_MODE;

public final class GitPatchWriter {
  final static @NonNls String GIT_DIFF_HEADER = "diff --git %s %s";
  private final static @NonNls String FILE_MODE_HEADER = "%s file mode %s";
  private final static @NonNls String INDEX_SHA1_HEADER = "index %s..%s";
  private final static @NonNls String FILE_RENAME_FROM_HEADER = "rename from %s";
  private final static @NonNls String FILE_RENAME_TO_HEADER = "rename to %s";


  @NotNull
  static String getFileModeHeader(@NotNull FileStatus fileStatus, int mode) {
    return String.format(FILE_MODE_HEADER, fileStatus == FileStatus.DELETED ? "deleted" : "new", mode); //NON-NLS NON-NLS
  }

  @NotNull
  static String getIndexHeader(@NotNull String beforeHash, @NotNull String afterHash) {
    return String.format(INDEX_SHA1_HEADER, beforeHash, afterHash);
  }

  public static void writeGitHeader(@NotNull Writer writer,
                                    @Nullable Path basePath,
                                    @NotNull FilePatch filePatch, @NotNull @NonNls String lineSeparator)
    throws IOException {
    writer.write(String.format(GIT_DIFF_HEADER,
                               UnifiedDiffWriter.A_PREFIX + filePatch.getBeforeName(),
                               UnifiedDiffWriter.B_PREFIX + filePatch.getAfterName()));
    writer.write(lineSeparator);
    if (filePatch.isDeletedFile()) {
      writer.write(getFileModeHeader(FileStatus.DELETED, REGULAR_FILE_MODE));
      writer.write(lineSeparator);
    }
    else if (filePatch.isNewFile()) {
      Path afterFile = basePath == null ? Paths.get(filePatch.getAfterName()) : basePath.resolve(filePatch.getAfterName());
      writer.write(getFileModeHeader(FileStatus.ADDED, !SystemInfo.isWindows && afterFile.toFile().canExecute()
                                                       ? EXECUTABLE_FILE_MODE : REGULAR_FILE_MODE));
      writer.write(lineSeparator);
    }
    else if (!StringUtil.equals(filePatch.getBeforeName(), filePatch.getAfterName())) {
      //movement
      writer.write(String.format(FILE_RENAME_FROM_HEADER, filePatch.getBeforeName()));
      writer.write(lineSeparator);
      writer.write(String.format(FILE_RENAME_TO_HEADER, filePatch.getAfterName()));
      writer.write(lineSeparator);
    }
  }
}
