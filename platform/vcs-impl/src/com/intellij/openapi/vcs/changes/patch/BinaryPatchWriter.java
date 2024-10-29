// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.BinaryEncoder;
import com.intellij.openapi.diff.impl.patch.BinaryFilePatch;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;

import static com.intellij.openapi.vcs.changes.patch.BlobIndexUtil.NOT_COMMITTED_HASH;
import static com.intellij.openapi.vcs.changes.patch.GitPatchWriter.getIndexHeader;
import static com.intellij.openapi.vcs.changes.patch.GitPatchWriter.writeGitHeader;

@ApiStatus.Internal
public final class BinaryPatchWriter {
  private final static Logger LOG = Logger.getInstance(BinaryFilePatch.class);

  private final static @NonNls String GIT_BINARY_HEADER = "GIT binary patch";
  private final static @NonNls String LITERAL_HEADER = "literal %s";

  public static void writeBinaries(@Nullable Path basePath,
                                   @NotNull List<BinaryFilePatch> patches,
                                   @NotNull Writer writer) throws IOException {
    String lineSeparator = "\n"; //use it for git headers&binary content, otherwise git won't parse&apply it properly
    for (BinaryFilePatch filePatch : patches) {
      writeGitHeader(writer, basePath, filePatch, lineSeparator);
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
        String afterFile = basePath == null ? filePatch.getAfterName() : basePath.resolve(filePatch.getAfterName()).toString();
        LOG.error("Can't write patch for binary file: " + afterFile, e);
      }
      writer.write(lineSeparator);
    }
  }

  @NotNull
  private static String getSha1ForContent(byte @Nullable [] content) {
    return content != null ? BlobIndexUtil.getSha1(content) : NOT_COMMITTED_HASH;
  }
}
