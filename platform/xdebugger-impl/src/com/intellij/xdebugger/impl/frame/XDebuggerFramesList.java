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
package com.intellij.xdebugger.impl.frame;

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.frame.XStackFrame;

import javax.swing.*;

/**
 * @author nik
 */
public class XDebuggerFramesList extends DebuggerFramesList {
  private XStackFrame mySelectedFrame;

  protected ListCellRenderer createListRenderer() {
    return new XDebuggerFrameListRenderer();
  }

  protected void onFrameChanged(final Object selectedValue) {
    if (mySelectedFrame != selectedValue) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          repaint();
        }
      });
      mySelectedFrame = (XStackFrame)selectedValue;
    }
  }

  private static class XDebuggerFrameListRenderer extends ColoredListCellRenderer {
    protected void customizeCellRenderer(final JList list,
                                         final Object value,
                                         final int index,
                                         final boolean selected,
                                         final boolean hasFocus) {
      if (value == null) {
        append(XDebuggerBundle.message("stack.frame.loading.text"), SimpleTextAttributes.GRAY_ATTRIBUTES);
        return;
      }
      if (value instanceof String) {
        append((String)value, SimpleTextAttributes.ERROR_ATTRIBUTES);
        return;
      }

      XStackFrame stackFrame = (XStackFrame)value;
      stackFrame.customizePresentation(this);
    }
  }
}
