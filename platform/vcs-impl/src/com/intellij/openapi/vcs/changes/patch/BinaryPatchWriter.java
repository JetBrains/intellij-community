// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.BinaryEncoder;
import com.intellij.openapi.diff.impl.patch.BinaryFilePatch;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

import static com.intellij.openapi.diff.impl.patch.PatchUtil.EXECUTABLE_FILE_MODE;
import static com.intellij.openapi.diff.impl.patch.PatchUtil.REGULAR_FILE_MODE;
import static com.intellij.openapi.vcs.changes.patch.BlobIndexUtil.NOT_COMMITTED_HASH;

public class BinaryPatchWriter {

  private final static Logger LOG = Logger.getInstance(BinaryFilePatch.class);

  private final static String GIT_DIFF_HEADER = "diff --git %s %s";
  private final static String FILE_MODE_HEADER = "%s file mode %d";
  private final static String INDEX_SHA1_HEADER = "index %s..%s";
  private final static String GIT_BINARY_HEADER = "GIT binary patch";
  private final static String LITERAL_HEADER = "literal %d";

  public static void writeBinaries(@Nullable String basePath,
                                   @NotNull List<? extends BinaryFilePatch> patches,
                                   @NotNull Writer writer) throws IOException {
    String lineSeparator = "\n"; //use it for git headers&binary content, otherwise git won't parse&apply it properly
    for (FilePatch patch : patches) {
      BinaryFilePatch filePatch = (BinaryFilePatch)patch;
      writer.write(String.format(GIT_DIFF_HEADER, filePatch.getBeforeName(), filePatch.getAfterName()));
      writer.write(lineSeparator);
      File afterFile = new File(basePath, filePatch.getAfterName());
      if (filePatch.isDeletedFile()) {
        writer.write(getFileModeHeader(FileStatus.DELETED, REGULAR_FILE_MODE));
        writer.write(lineSeparator);
      }
      else if (filePatch.isNewFile()) {
        writer.write(getFileModeHeader(FileStatus.ADDED, !SystemInfo.isWindows && afterFile.canExecute()
                                                         ? EXECUTABLE_FILE_MODE : REGULAR_FILE_MODE));
        writer.write(lineSeparator);
      }
      byte[] afterContent = filePatch.getAfterContent();
      writer.write(getIndexHeader(filePatch.isNewFile() ? NOT_COMMITTED_HASH : getSha1ForContent(filePatch.getBeforeContent()),
                                  filePatch.isDeletedFile() ? NOT_COMMITTED_HASH : getSha1ForContent(afterContent)));
      writer.write(lineSeparator);
      writer.write(GIT_BINARY_HEADER);
      writer.write(lineSeparator);
      writer.write(String.format(LITERAL_HEADER, afterContent == null ? 0 : afterContent.length));
      writer.write(lineSeparator);
      try {
        BinaryEncoder.encode(new ByteArrayInputStream(afterContent != null ? afterContent : ArrayUtilRt.EMPTY_BYTE_ARRAY), writer);
      }
      catch (BinaryEncoder.BinaryPatchException e) {
        LOG.error("Can't write patch for binary file: " + afterFile.getPath(), e);
      }
      writer.write(lineSeparator);
    }
  }

  @NotNull
  private static String getFileModeHeader(@NotNull FileStatus fileStatus, int mode) {
    return String.format(FILE_MODE_HEADER, fileStatus == FileStatus.DELETED ? "deleted" : "new", mode);
  }

  @NotNull
  private static String getIndexHeader(@NotNull String beforeHash, @NotNull String afterHash) {
    return String.format(INDEX_SHA1_HEADER, beforeHash, afterHash);
  }

  @NotNull
  private static String getSha1ForContent(@Nullable byte[] content) {
    return content != null ? BlobIndexUtil.getSha1(content) : NOT_COMMITTED_HASH;
  }
}
