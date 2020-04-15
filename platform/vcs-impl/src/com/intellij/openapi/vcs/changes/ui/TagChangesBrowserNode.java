// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import org.jetbrains.annotations.NotNull;

public class TagChangesBrowserNode extends ChangesBrowserNode<Object> {
  private final boolean myExpandByDefault;

  public TagChangesBrowserNode(@NotNull Object userObject, boolean expandByDefault) {
    super(userObject);
    myExpandByDefault = expandByDefault;
  }

  @Override
  public boolean shouldExpandByDefault() {
    return myExpandByDefault;
  }
}
