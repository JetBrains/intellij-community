// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs;

public interface RepositoryLocation {
  /**
   * those shown in, for instance, Changes Browser tool window title
   */
  String toPresentableString();

  /**
   * must uniquely identify this location
   */
  String getKey();

  default void onBeforeBatch() throws VcsException {
  }

  default void onAfterBatch() {
  }
}
