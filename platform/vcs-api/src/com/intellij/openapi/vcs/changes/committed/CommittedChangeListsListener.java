// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.NotNull;

public interface CommittedChangeListsListener {
  void onBeforeStartReport();

  /**
   * @return true - continue reporting
   */
  boolean report(@NotNull CommittedChangeList list);

  void onAfterEndReport();
}
