// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

@ApiStatus.Internal
public class ChangesBrowserRootNode extends ChangesBrowserNode<@NonNls String> {
  private static final @NonNls String ROOT_NODE_VALUE = "root";

  public ChangesBrowserRootNode() {
    super(ROOT_NODE_VALUE);
  }

  @Override
  public @Nls String getTextPresentation() {
    return "";
  }
}
