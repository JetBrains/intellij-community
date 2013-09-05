/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.xdebugger.frame.presentation;

import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XGroupingValuePresentation extends XValuePresentation {
  public static final XGroupingValuePresentation INSTANCE = new XGroupingValuePresentation(null);
  private final String myComment;

  public XGroupingValuePresentation(@Nullable String comment) {
    myComment = comment;
  }

  @Nullable
  @Override
  public SimpleTextAttributes getNameAttributes() {
    return SimpleTextAttributes.REGULAR_ATTRIBUTES;
  }

  @NotNull
  @Override
  public String getSeparator() {
    return myComment != null ? super.getSeparator() : "";
  }

  @Override
  public void renderValue(@NotNull XValueTextRenderer renderer) {
    if (myComment != null) {
      renderer.renderComment(myComment);
    }
  }
}