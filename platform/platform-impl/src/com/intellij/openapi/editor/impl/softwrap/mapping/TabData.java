/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.ui.TabbedPaneWrapper;
import org.jetbrains.annotations.NotNull;

/**
* @author Denis Zhdanov
* @since Sep 9, 2010 9:21:22 AM
*/
class TabData implements Cloneable {

  public final int widthInColumns;
  public int offset;

  TabData(@NotNull ProcessingContext context) {
    widthInColumns = context.symbolWidthInColumns;
    offset = context.offset;
  }

  TabData(int widthInColumns, int offset) {
    this.widthInColumns = widthInColumns;
    this.offset = offset;
  }

  @Override
  public String toString() {
    return offset + ", width: " + widthInColumns;
  }

  @Override
  protected TabData clone() {
    return new TabData(widthInColumns, offset);
  }
}
