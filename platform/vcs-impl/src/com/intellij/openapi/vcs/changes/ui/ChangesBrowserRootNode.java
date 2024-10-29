// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

@ApiStatus.Internal
public class ChangesBrowserRootNode extends ChangesBrowserNode<@NonNls String> {
  @NonNls private static final String ROOT_NODE_VALUE = "root";

  public ChangesBrowserRootNode() {
    super(ROOT_NODE_VALUE);
  }

  @Override
  public @Nls String getTextPresentation() {
    return "";
  }
}
