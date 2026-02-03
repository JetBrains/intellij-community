// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class ChangesBrowserStringNode extends ChangesBrowserNode<@Nls String> {
  private final SimpleTextAttributes myAttributes;

  public ChangesBrowserStringNode(@NotNull @Nls String userObject) {
    this(userObject, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public ChangesBrowserStringNode(@NotNull @Nls String userObject, @NotNull SimpleTextAttributes attributes) {
    super(userObject);
    myAttributes = attributes;
  }

  @Override
  public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    renderer.append(getTextPresentation(), myAttributes);
    appendCount(renderer);
  }

  @Override
  public @Nls String getTextPresentation() {
    return getUserObject();
  }
}
