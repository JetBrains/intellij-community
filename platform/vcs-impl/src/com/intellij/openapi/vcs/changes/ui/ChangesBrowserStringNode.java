// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class ChangesBrowserStringNode extends ChangesBrowserNode<@Nls String> {
  public ChangesBrowserStringNode(@NotNull @Nls String userObject) {
    super(userObject);
  }

  @Override
  public @Nls String getTextPresentation() {
    return getUserObject();
  }
}
