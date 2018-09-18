/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;

public class Cvs2Renderer extends ColoredTreeCellRenderer {
  @Override
  public void customizeCellRenderer(JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    if (value instanceof CvsElement) {
      final CvsElement element = (CvsElement)value;
      append(value.toString(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN,
                                                        UIUtil.getTreeForeground()));
      if (element.isLoading()) {
        append(" (Loading...)", new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN,
                                                         UIUtil.getInactiveTextColor()));
      }
      setIcon(element.getIcon());
    } else if (value instanceof LoadingNode) {
      setIcon(((LoadingNode)value).getIcon());
      append(value.toString(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN,
                                                        UIUtil.getInactiveTextColor()));
    }
  }
}
