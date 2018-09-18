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
package com.intellij.cvsSupport2.ui;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * author: lesya
 */
public abstract class AbstractListCellRenderer extends DefaultListCellRenderer {
  protected abstract String getPresentableString(Object value);
  @Nullable
  protected Icon getPresentableIcon(Object value){
    return null;
  }

  @Override
  public Component getListCellRendererComponent(
      JList list,
      Object value,
      int index,
      boolean isSelected,
      boolean cellHasFocus) {

    Component result = super.getListCellRendererComponent(list, getPresentableString(value), index, isSelected, cellHasFocus);

    Icon icon = getPresentableIcon(value);
    if (icon != null)
      setIcon(icon);

    return result;
  }

}
