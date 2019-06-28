// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ChangeNodeDecorator {
  void decorate(@NotNull Change change, @NotNull SimpleColoredComponent component, boolean isShowFlatten);

  void preDecorate(@NotNull Change change, @NotNull ChangesBrowserNodeRenderer renderer, boolean isShowFlatten);


  @SuppressWarnings("unused")
  @Nullable
  @Deprecated
  default List<Pair<String, Stress>> stressPartsOfFileName(final Change change, final String parentPath) { return null; }

  @SuppressWarnings("unused")
  @Deprecated
  enum Stress {
    BOLD(SimpleTextAttributes.STYLE_BOLD),
    ITALIC(SimpleTextAttributes.STYLE_ITALIC),
    BOLD_ITALIC(SimpleTextAttributes.STYLE_BOLD | SimpleTextAttributes.STYLE_ITALIC),
    PLAIN(SimpleTextAttributes.STYLE_PLAIN);

    @SimpleTextAttributes.StyleAttributeConstant
    private final int myFontStyle;

    Stress(@SimpleTextAttributes.StyleAttributeConstant int fontStyle) {
      myFontStyle = fontStyle;
    }

    public int getFontStyle() {
      return myFontStyle;
    }

    public SimpleTextAttributes derive(final SimpleTextAttributes attributes) {
      return attributes.derive(myFontStyle, attributes.getFgColor(), attributes.getBgColor(), attributes.getWaveColor());
    }
  }
}
