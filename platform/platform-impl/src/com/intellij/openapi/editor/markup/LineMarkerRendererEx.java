/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.editor.Editor;

import java.awt.*;

public interface LineMarkerRendererEx extends LineMarkerRenderer {
  enum Position {LEFT, RIGHT}
  
  /**
   * Determines whether line marker should be rendered to the left or to the right of icon area in gutter.
   * Corresponding rectangle will be passed to renderer in {@link #paint(Editor, Graphics, Rectangle)} method.
   */
  Position getPosition();
}
