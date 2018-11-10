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
package com.intellij.usages;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author max
 */
public interface UsagePresentation {
  @NotNull
  TextChunk[] getText();

  /**
   * If the implementation caches or lazy-loades the text chunks internally, this method gives it a chance to avoid
   * re-calculating it synchronously on EDT and return the possibly obsolete data.
   *
   * The component using this presentation might call {@link UsagePresentation#updateCachedText()} in a background
   * thread and then use {@link UsagePresentation#getCachedText()} to draw the text.
   */
  @Nullable
  default TextChunk[] getCachedText() {
    return getText();
  }

  default void updateCachedText() {}

  @NotNull
  String getPlainText();

  Icon getIcon();

  String getTooltipText();
}
