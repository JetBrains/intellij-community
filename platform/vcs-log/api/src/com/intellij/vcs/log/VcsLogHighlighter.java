/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log;

import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Allows to add some highlighting to the Vcs Log table entries.
 */
public interface VcsLogHighlighter {

  /**
   * Return the color which should be used for the log table entries foreground, or null if default color should be used.
   * @param commitIndex index of commit (can be transferred to the Hash and vice versa).
   * @param isSelected  if true, the row currently has selection on it.
   */
  @Nullable
  Color getForeground(int commitIndex, boolean isSelected);

}
