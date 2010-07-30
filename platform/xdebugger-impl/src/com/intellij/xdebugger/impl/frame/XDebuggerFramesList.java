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

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.FileColorManager;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XStackFrame;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class XDebuggerFramesList extends DebuggerFramesList {
  private XStackFrame mySelectedFrame;

  public XDebuggerFramesList(Project project) {
    super(project);
    doInit();
  }

  protected ListCellRenderer createListRenderer() {
    return new XDebuggerFrameListRenderer(myProject);
  }

  protected void onFrameChanged(final Object selectedValue) {
    if (mySelectedFrame != selectedValue) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          repaint();
        }
      });
      if (selectedValue instanceof XStackFrame) {
        mySelectedFrame = (XStackFrame)selectedValue;
      }
      else {
        mySelectedFrame = null;
      }
    }
  }

  private static class XDebuggerFrameListRenderer extends ColoredListCellRenderer {
    private final FileColorManager myColorsManager;
    private final PsiManager myPsiManager;

    public XDebuggerFrameListRenderer(Project project) {
      myPsiManager = PsiManager.getInstance(project);
      myColorsManager = FileColorManager.getInstance(project);
    }

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
      if (!selected) {
        XSourcePosition position = stackFrame.getSourcePosition();
        if (position != null) {
          PsiFile f = myPsiManager.findFile(position.getFile());
          if (f != null) {
            Color c = myColorsManager.getFileColor(f);
            if (c != null) setBackground(c);
          }
        }
      }
      stackFrame.customizePresentation(this);
    }
  }
}
