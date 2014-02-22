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
package com.intellij.vcs.log.graph;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

public interface PaintInfo {

  /**
   * Returns the image to actually paint.
   */
  @NotNull
  Image getImage();

  /**
   * Returns the "interesting" width of the painted image, i.e. the width which the text in the table should be offset by. <br/>
   * It can be smaller than the width of {@link #getImage() the image}, because we allow the text to cover part of the graph
   * (some diagonal edges, etc.)
   */
  int getWidth();

}
