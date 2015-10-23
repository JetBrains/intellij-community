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
package com.intellij.xdebugger.impl.frame;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.FileColorManager;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.TextTransferable;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XStackFrame;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Transferable;

/**
 * @author nik
 */
public class XDebuggerFramesList extends DebuggerFramesList {
  private final Project myProject;

  private static final TransferHandler DEFAULT_TRANSFER_HANDLER = new TransferHandler() {
    @Override
    protected Transferable createTransferable(JComponent c) {
      if (!(c instanceof XDebuggerFramesList)) {
        return null;
      }
      XDebuggerFramesList list = (XDebuggerFramesList)c;
      //noinspection deprecation
      Object[] values = list.getSelectedValues();
      if (values == null || values.length == 0) {
        return null;
      }

      StringBuilder plainBuf = new StringBuilder();
      StringBuilder htmlBuf = new StringBuilder();
      TextTransferable.ColoredStringBuilder coloredTextContainer = new TextTransferable.ColoredStringBuilder();
      htmlBuf.append("<html>\n<body>\n<ul>\n");
      for (Object value : values) {
        htmlBuf.append("  <li>");
        if (value != null) {
          if (value instanceof XStackFrame) {
            ((XStackFrame)value).customizePresentation(coloredTextContainer);
            coloredTextContainer.appendTo(plainBuf, htmlBuf);
          }
          else {
            String text = value.toString();
            plainBuf.append(text);
            htmlBuf.append(text);
          }
        }
        plainBuf.append('\n');
        htmlBuf.append("</li>\n");
      }

      // remove the last newline
      plainBuf.setLength(plainBuf.length() - 1);
      htmlBuf.append("</ul>\n</body>\n</html>");
      return new TextTransferable(htmlBuf.toString(), plainBuf.toString());
    }

    @Override
    public int getSourceActions(@NotNull JComponent c) {
      return COPY;
    }
  };

  private XStackFrame mySelectedFrame;

  public XDebuggerFramesList(@NotNull Project project) {
    myProject = project;

    doInit();
    setTransferHandler(DEFAULT_TRANSFER_HANDLER);
    setDataProvider(new DataProvider() {
      @Nullable
      @Override
      public Object getData(@NonNls String dataId) {
        if (mySelectedFrame != null) {
          if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
            return getFile(mySelectedFrame);
          }
          else if (CommonDataKeys.PSI_FILE.is(dataId)) {
            VirtualFile file = getFile(mySelectedFrame);
            if (file != null && file.isValid()) {
              return PsiManager.getInstance(myProject).findFile(file);
            }
          }
        }
        return null;
      }
    });
  }

  @Nullable
  private static VirtualFile getFile(XStackFrame frame) {
    XSourcePosition position = frame.getSourcePosition();
    return position != null ? position.getFile() : null;
  }

  @Override
  protected ListCellRenderer createListRenderer() {
    return new XDebuggerFrameListRenderer(myProject);
  }

  @Override
  protected void onFrameChanged(final Object selectedValue) {
    if (mySelectedFrame != selectedValue) {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
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

    public XDebuggerFrameListRenderer(@NotNull Project project) {
      myColorsManager = FileColorManager.getInstance(project);
    }

    @Override
    protected void customizeCellRenderer(final JList list,
                                         final Object value,
                                         final int index,
                                         final boolean selected,
                                         final boolean hasFocus) {
      // Fix GTK background
      if (UIUtil.isUnderGTKLookAndFeel()){
        final Color background = selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground();
        UIUtil.changeBackGround(this, background);
      }
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
          final VirtualFile virtualFile = position.getFile();
          if (virtualFile.isValid()) {
            Color c = myColorsManager.getFileColor(virtualFile);
            if (c != null) setBackground(c);
          }
        }
      }
      stackFrame.customizePresentation(this);
    }
  }
}
