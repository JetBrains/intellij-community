// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;

class FixedHeightSampleChangesBrowserNode extends ChangesBrowserNode<Object> {
  private static final Object FIXED_HEIGHT_SAMPLE_NODE_VALUE = new Object();

  FixedHeightSampleChangesBrowserNode() {
    super(FIXED_HEIGHT_SAMPLE_NODE_VALUE);
  }

  @Override
  public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    renderer.append("ChangesTreeDummy.java");
    renderer.setIcon(AllIcons.FileTypes.Any_type);
  }

  @Override
  public String toString() {
    return "FixedHeightSampleChangesBrowserNode";
  }
}
