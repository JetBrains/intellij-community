// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.frame;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.scope.NonProjectFilesScope;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.FileColorManager;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.TextTransferable;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XDebuggerFramesList extends DebuggerFramesList {
  private final Project myProject;
  private final Map<VirtualFile, Color> myFileColors = new HashMap<>();
  private static final DataKey<XDebuggerFramesList> FRAMES_LIST = DataKey.create("FRAMES_LIST");

  private void copyStack() {
    List items = getModel().getItems();
    if (!items.isEmpty()) {
      StringBuilder plainBuf = new StringBuilder();
      TextTransferable.ColoredStringBuilder coloredTextContainer = new TextTransferable.ColoredStringBuilder();
      for (Object value : items) {
        if (value instanceof ItemWithSeparatorAbove) {
          ItemWithSeparatorAbove item = (ItemWithSeparatorAbove)value;
          if (item.hasSeparatorAbove()) {
            String caption = " - " + StringUtil.notNullize(item.getCaptionAboveOf());
            plainBuf.append(caption).append('\n');
          }
        }

        if (value != null) {
          if (value instanceof XStackFrame) {
            ((XStackFrame)value).customizePresentation(coloredTextContainer);
            coloredTextContainer.appendTo(plainBuf);
          }
          else {
            String text = value.toString();
            plainBuf.append(text);
          }
        }
        plainBuf.append('\n');
      }

      // remove the last newline
      plainBuf.setLength(plainBuf.length() - 1);
      String plainText = plainBuf.toString();
      CopyPasteManager.getInstance().setContents(
        new TextTransferable("<html><body><pre>\n" + XmlStringUtil.escapeString(plainText) + "\n</pre></body></html>", plainText));
    }
  }

  private XStackFrame mySelectedFrame;

  public XDebuggerFramesList(@NotNull Project project) {
    myProject = project;

    doInit();
    setDataProvider(dataId -> {
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
      if (FRAMES_LIST.is(dataId)) {
        return XDebuggerFramesList.this;
      }
      return null;
    });

    // This is a workaround for the performance issue IDEA-187063
    // default font generates too much garbage in deriveFont
    Font font = getFont();
    if (font != null) {
      setFont(new FontUIResource(font.getName(), font.getStyle(), font.getSize()));
    }
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
      SwingUtilities.invokeLater(this::repaint);
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

    XDebuggerGroupedFrameListRenderer() {
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

    XDebuggerFrameListRenderer(@NotNull Project project) {
      myColorsManager = FileColorManager.getInstance(project);
    }

    @Override
    protected void customizeCellRenderer(@NotNull final JList list,
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
        Color c = getFrameBgColor(stackFrame);
        if (c != null) {
          setBackground(c);
        }
      }
      else if (Registry.is("debugger.new.debug.tool.window.view")){
        setBackground(UIUtil.getListSelectionBackground(hasFocus));
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

  public static class CopyStackAction extends DumbAwareAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
      XDebuggerFramesList framesList = e.getData(FRAMES_LIST);
      //noinspection unchecked
      e.getPresentation().setEnabledAndVisible(framesList != null && ContainerUtil.getLastItem(framesList.getModel().getItems()) != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      XDebuggerFramesList framesList = e.getData(FRAMES_LIST);
      if (framesList != null) {
        framesList.copyStack();
      }
    }
  }
}
