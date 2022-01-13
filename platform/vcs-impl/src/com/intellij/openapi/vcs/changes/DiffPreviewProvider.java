// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.diff.impl.DiffRequestProcessor;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.OverrideOnly
public interface DiffPreviewProvider {
  @NotNull
  DiffRequestProcessor createDiffRequestProcessor();

  @NotNull
  Object getOwner();

  /**
   * @deprecated Implement {@link #getEditorTabName(DiffRequestProcessor)}
   */
  @Deprecated
  @NlsContexts.TabTitle
  default String getEditorTabName() {
    throw new UnsupportedOperationException("Not implemented");
  }


  @NlsContexts.TabTitle
  default String getEditorTabName(@Nullable DiffRequestProcessor processor) {
    return getEditorTabName();
  }
}
