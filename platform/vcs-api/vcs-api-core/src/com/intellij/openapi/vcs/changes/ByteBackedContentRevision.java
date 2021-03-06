// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.Nullable;

public interface ByteBackedContentRevision extends ContentRevision {
  /**
   * Content of the revision. Implementers are encouraged to lazy implement this especially when it requires connection to the
   * version control server or something.
   * Might return null in case if file path denotes a directory or content is impossible to retrieve.
   *
   * @return content of the revision
   * @throws VcsException in case when content retrieval fails
   */
  byte @Nullable [] getContentAsBytes() throws VcsException;
}
