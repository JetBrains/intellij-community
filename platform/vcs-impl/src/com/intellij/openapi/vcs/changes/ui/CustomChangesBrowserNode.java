// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui;

import org.jetbrains.annotations.NotNull;

public class CustomChangesBrowserNode extends ChangesBrowserNode<Object> {
  @NotNull private final Provider myProvider;

  protected CustomChangesBrowserNode(@NotNull Provider provider) {
    super(provider.getUserObject());
    myProvider = provider;
  }

  @Override
  public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    myProvider.render(renderer, selected, expanded, hasFocus);
  }

  @Override
  public String getTextPresentation() {
    return myProvider.getTextPresentation();
  }

  public interface Provider {
    @NotNull
    default String getTextPresentation() {
      return toString();
    }

    @NotNull
    default Object getUserObject() {
      return this;
    }

    void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus);
  }
}
