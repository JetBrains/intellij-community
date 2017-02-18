/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.patch;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.BinaryContentRevision;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import static com.intellij.util.ArrayUtil.EMPTY_BYTE_ARRAY;

public class BlobIndexUtil {

  public static final String NOT_COMMITTED_HASH = StringUtil.repeat("0", 40);


  @NotNull
  public static String getSha1(@NotNull File file) throws IOException {
    return getSha1(Files.toByteArray(file));
  }

  /**
   * Generate sha1 for file content using git-like algorithm
   */
  @NotNull
  public static String getSha1(@NotNull byte[] bytes) {
    String prefix = "blob " + bytes.length + '\u0000';
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

  @NotNull
  private static byte[] getContentBytes(@NotNull ContentRevision revision, @NotNull Charset charset) throws VcsException {
    byte[] binaryContent;
    if (revision instanceof BinaryContentRevision) {
      binaryContent = ((BinaryContentRevision)revision).getBinaryContent();
    }
    else {
      String stringContent = revision.getContent();
      binaryContent = stringContent != null ? stringContent.getBytes(charset) : EMPTY_BYTE_ARRAY;
    }
    return binaryContent != null ? binaryContent : EMPTY_BYTE_ARRAY;
  }
}
