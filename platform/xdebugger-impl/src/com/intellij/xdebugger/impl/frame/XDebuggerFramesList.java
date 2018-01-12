/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger.impl.frame;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.scope.NonProjectFilesScope;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.FileColorManager;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.util.containers.HashMap;
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
import java.util.Map;

/**
 * @author nik
 */
public class XDebuggerFramesList extends DebuggerFramesList {
  private final Project myProject;
  private final Map<VirtualFile, Color> myFileColors = new HashMap<>();

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

  @Override
  public void clear() {
    super.clear();
    myFileColors.clear();
  }

  @Nullable
  private static VirtualFile getFile(XStackFrame frame) {
    XSourcePosition position = frame.getSourcePosition();
    return position != null ? position.getFile() : null;
  }

  @Override
  protected ListCellRenderer createListRenderer() {
    return new XDebuggerGroupedFrameListRenderer();
  }

  @Override
  protected void onFrameChanged(final Object selectedValue) {
    if (mySelectedFrame != selectedValue) {
      SwingUtilities.invokeLater(() -> repaint());
      if (selectedValue instanceof XStackFrame) {
        mySelectedFrame = (XStackFrame)selectedValue;
      }
      else {
        mySelectedFrame = null;
      }
    }
  }

  private class XDebuggerGroupedFrameListRenderer extends GroupedItemsListRenderer {
    private final XDebuggerFrameListRenderer myOriginalRenderer = new XDebuggerFrameListRenderer(myProject);

    public XDebuggerGroupedFrameListRenderer() {
      super(new ListItemDescriptorAdapter() {
        @Nullable
        @Override
        public String getTextFor(Object value) {
          return null;
        }

        @Nullable
        @Override
        public String getCaptionAboveOf(Object value) {
          return value instanceof ItemWithSeparatorAbove ? ((ItemWithSeparatorAbove)value).getCaptionAboveOf() : null;
        }

        @Override
        public boolean hasSeparatorAboveOf(Object value) {
          return value instanceof ItemWithSeparatorAbove && ((ItemWithSeparatorAbove)value).hasSeparatorAbove();
        }
      });
      mySeparatorComponent.setCaptionCentered(false);
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      if (myDescriptor.hasSeparatorAboveOf(value)) {
        Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        ((XDebuggerFrameListRenderer)myComponent).getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        return component;
      }
      else {
        return myOriginalRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      }
    }

    @Override
    protected JComponent createItemComponent() {
      createLabel();
      return new XDebuggerFrameListRenderer(myProject);
    }
  }

  private class XDebuggerFrameListRenderer extends ColoredListCellRenderer {
    private final FileColorManager myColorsManager;

    public XDebuggerFrameListRenderer(@NotNull Project project) {
      myColorsManager = FileColorManager.getInstance(project);
    }

    @Override
    protected void customizeCellRenderer(@NotNull final JList list,
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
        Color c = getFrameBgColor(stackFrame);
        if (c != null) {
          setBackground(c);
        }
      }
      stackFrame.customizePresentation(this);
    }

    Color getFrameBgColor(XStackFrame stackFrame) {
      if (stackFrame instanceof ItemWithCustomBackgroundColor) {
        return ((ItemWithCustomBackgroundColor)stackFrame).getBackgroundColor();
      }
      VirtualFile virtualFile = getFile(stackFrame);
      if (virtualFile != null) {
        // handle null value
        if (myFileColors.containsKey(virtualFile)) {
          return myFileColors.get(virtualFile);
        }
        else if (virtualFile.isValid()) {
          Color color = myColorsManager.getFileColor(virtualFile);
          myFileColors.put(virtualFile, color);
          return color;
        }
      }
      else {
        return myColorsManager.getScopeColor(NonProjectFilesScope.NAME);
      }
      return null;
    }
  }

  public interface ItemWithSeparatorAbove {
    boolean hasSeparatorAbove();
    String getCaptionAboveOf();
  }

  public interface ItemWithCustomBackgroundColor {
    @Nullable
    Color getBackgroundColor();
  }
}
