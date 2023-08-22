// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

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

    String before = beforeRevision == null ? NOT_COMMITTED_HASH : getSha1(getContentBytes(beforeRevision));
    String after = afterRevision == null ? NOT_COMMITTED_HASH : getSha1(getContentBytes(afterRevision));
    return new Couple<>(before, after);
  }

  private static byte @NotNull [] getContentBytes(@NotNull ContentRevision revision) throws VcsException {
    return ChangesUtil.loadContentRevision(revision);
  }
}
