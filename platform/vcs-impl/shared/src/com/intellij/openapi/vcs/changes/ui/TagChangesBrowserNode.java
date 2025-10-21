// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class TagChangesBrowserNode extends ChangesBrowserNode<ChangesBrowserNode.Tag> {
  private final SimpleTextAttributes myAttributes;
  private final boolean myExpandByDefault;

  public TagChangesBrowserNode(@NotNull ChangesBrowserNode.Tag userObject,
                               @NotNull SimpleTextAttributes attributes,
                               boolean expandByDefault) {
    super(userObject);
    myAttributes = attributes;
    myExpandByDefault = expandByDefault;
  }

  @Override
  public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    renderer.append(getTextPresentation(), myAttributes);
    appendCount(renderer);
  }

  @Override
  public @Nls String getTextPresentation() {
    return getUserObject().toString();
  }

  @Override
  public boolean shouldExpandByDefault() {
    return myExpandByDefault;
  }
}
