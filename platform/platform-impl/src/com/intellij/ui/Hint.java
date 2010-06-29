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
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface Hint {
  /**
   * @param parentComponent    defines coordinate system where hint will be shown.
   *                           Cannot be <code>null</code>.
   * @param x                  x coordinate of hint in parent coordinate system
   * @param y                  y coordinate of hint in parent coordinate system
   * @param focusBackComponent component which should get focus when the hint will
   *                           be hidden. If <code>null</code> then the hint doesn't manage focus after closing.
   */
  void show(@NotNull JComponent parentComponent, int x, int y, JComponent focusBackComponent);

  /**
   * @return whether the hint is showing or not
   */
  boolean isVisible();

  void hide();

  void addHintListener(HintListener listener);

  void removeHintListener(HintListener listener);

  void updateBounds();

  void updateBounds(int x, int y);
}