// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ByteBackedContentRevision;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

public final class BlobIndexUtil {

  public static final String NOT_COMMITTED_HASH = StringUtil.repeat("0", 40);


  @NotNull
  public static String getSha1(@NotNull File file) throws IOException {
    return getSha1(Files.toByteArray(file));
  }

  /**
   * Generate sha1 for file content using git-like algorithm
   */
  @NotNull
  public static String getSha1(byte @NotNull [] bytes) {
    String prefix = "blob " + bytes.length + '\u0000'; //NON-NLS
    return Hashing.sha1().newHasher().putBytes(prefix.getBytes(Charsets.UTF_8)).putBytes(bytes).hash().toString();
  }

  @NotNull
  public static Couple<String> getBeforeAfterSha1(@NotNull Change change) throws VcsException {
    ContentRevision beforeRevision = change.getBeforeRevision();
    ContentRevision afterRevision = change.getAfterRevision();

    //noinspection ConstantConditions
    Charset detectCharset = ObjectUtils.chooseNotNull(afterRevision, beforeRevision).getFile().getCharset();
    String before = beforeRevision == null ? NOT_COMMITTED_HASH : getSha1(getContentBytes(beforeRevision, detectCharset));
    String after = afterRevision == null ? NOT_COMMITTED_HASH : getSha1(getContentBytes(afterRevision, detectCharset));
    return new Couple<>(before, after);
  }

  private static byte @NotNull [] getContentBytes(@NotNull ContentRevision revision, @NotNull Charset charset) throws VcsException {
    byte[] binaryContent;
    if (revision instanceof ByteBackedContentRevision) {
      binaryContent = ((ByteBackedContentRevision)revision).getContentAsBytes();
    }
    else {
      String stringContent = revision.getContent();
      binaryContent = stringContent != null ? stringContent.getBytes(charset) : ArrayUtilRt.EMPTY_BYTE_ARRAY;
    }
    return binaryContent != null ? binaryContent : ArrayUtilRt.EMPTY_BYTE_ARRAY;
  }
}
