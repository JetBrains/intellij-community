// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

/**
 * @deprecated Use {@link CommitContext} passed to {@link CommitExecutor#createCommitSession(CommitContext)} instead.
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
public interface CommitSessionContextAware {
  void setContext(final CommitContext context);
}
