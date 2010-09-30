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
package com.intellij.ide.ui;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
* @author oleg
* @date 9/30/10
*/
public class ListCellRendererWrapper implements ListCellRenderer {
  private final ListCellRenderer myWrapped;

  public ListCellRendererWrapper(final ListCellRenderer listCellRenderer) {
    this.myWrapped = listCellRenderer;
  }

  public Component getListCellRendererComponent(final JList list,
                                                final Object value,
                                                final int index,
                                                final boolean isSelected,
                                                final boolean cellHasFocus) {
    final String displayName = getDisplayedName(value);
    return myWrapped.getListCellRendererComponent(list, displayName, index, isSelected, cellHasFocus);
  }

  @Nullable
  public String getDisplayedName(Object value) {
    return String.valueOf(value);
  }
}
