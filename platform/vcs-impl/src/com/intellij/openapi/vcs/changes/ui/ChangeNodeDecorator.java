/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ChangeNodeDecorator {
  void decorate(final Change change, final SimpleColoredComponent component, boolean isShowFlatten);
  @Nullable
  List<Pair<String, Stress>> stressPartsOfFileName(final Change change, final String parentPath);

  void preDecorate(Change change, ChangesBrowserNodeRenderer renderer, boolean showFlatten);

  enum Stress {
    BOLD(SimpleTextAttributes.STYLE_BOLD),
    ITALIC(SimpleTextAttributes.STYLE_ITALIC),
    BOLD_ITALIC(SimpleTextAttributes.STYLE_BOLD | SimpleTextAttributes.STYLE_ITALIC),
    PLAIN(SimpleTextAttributes.STYLE_PLAIN);

    @SimpleTextAttributes.StyleAttributeConstant
    private final int myFontStyle;

    private Stress(@SimpleTextAttributes.StyleAttributeConstant int fontStyle) {
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
