// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.vcs.VcsException;

public interface VcsAppendableHistorySessionPartner {
  void reportCreatedEmptySession(VcsAbstractHistorySession session);
  void acceptRevision(VcsFileRevision revision);
  void reportException(VcsException exception);
}
