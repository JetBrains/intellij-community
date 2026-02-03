// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.local;

import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface ChangeListCommand {
  void apply(final ChangeListWorker worker);

  void doNotify(final ChangeListListener listener);
}
