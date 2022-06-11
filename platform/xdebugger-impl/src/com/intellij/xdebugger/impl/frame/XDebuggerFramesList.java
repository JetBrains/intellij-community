// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.frame;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.HelpTooltip;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.scope.NonProjectFilesScope;
import com.intellij.ui.*;
import com.intellij.ui.hover.HoverListener;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBScalableIcon;
import com.intellij.util.ui.TextTransferable;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XDropFrameHandler;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;

public class XDebuggerFramesList extends DebuggerFramesList implements DataProvider {
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

  public XDebuggerFramesList(@NotNull Project project) {
    myProject = project;

    doInit();

    // This is a workaround for the performance issue IDEA-187063
    // default font generates too much garbage in deriveFont
    Font font = getFont();
    if (font != null) {
      setFont(new FontUIResource(font.getName(), font.getStyle(), font.getSize()));
    }

    new XDebuggerFrameListMouseListener().installListeners(this);

    final ResetFrameAction resetFrameAction = new ResetFrameAction();
    resetFrameAction.registerCustomShortcutSet(CommonShortcuts.getDelete(), this);
    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        var list = ObjectUtils.tryCast(e.getComponent(), XDebuggerFramesList.class);
        if (list != null && XDebuggerFrameListMouseListener.getResetIconHovered(list)) {
          ActionManager.getInstance().tryToExecute(
            resetFrameAction, e, list, "XDebuggerFramesList", true
          );
        }
      }
    });
  }

  @Override
  public @Nullable Object getData(@NonNls @NotNull String dataId) {
    if (FRAMES_LIST.is(dataId)) {
      return this;
    }
    // Because of the overridden locationToIndex(), the default logic of retrieving the context menu point doesn't work.
    // Here, were mimic the way PopupFactoryImpl.guessBestPopupLocation(JComponent) calculates it for JLists.
    if (PlatformDataKeys.CONTEXT_MENU_POINT.is(dataId)) {
      int index = getSelectedIndex();
      if (index != -1) {
        Rectangle cellBounds = getCellBounds(index, index);
        if (cellBounds != null) {
          Rectangle visibleRect = getVisibleRect();
          return new Point(visibleRect.x + visibleRect.width / 4, cellBounds.y + cellBounds.height - 1);
        }
      }
      return null;
    }
    if (PlatformCoreDataKeys.SLOW_DATA_PROVIDERS.is(dataId)) {
      XStackFrame frame = getSelectedFrame();
      if (frame != null) {
        return List.<DataProvider>of(realDataId -> getSlowData(frame, realDataId));
      }
    }
    return null;
  }

  @Nullable
  private Object getSlowData(@NotNull XStackFrame frame, @NonNls String dataId) {
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      return getFrameNavigatable(frame);
    }
    if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      return getFile(frame);
    }
    if (CommonDataKeys.PSI_FILE.is(dataId)) {
      VirtualFile file = getFile(frame);
      if (file != null && file.isValid()) {
        return PsiManager.getInstance(myProject).findFile(file);
      }
    }
    return null;
  }

  @Override
  public void clear() {
    super.clear();
    myFileColors.clear();
  }

  public boolean selectFrame(@NotNull XStackFrame toSelect) {
    //noinspection unchecked
    if (!Objects.equals(getSelectedValue(), toSelect) && getModel().contains(toSelect)) {
      setSelectedValue(toSelect, true);
      return true;
    }
    return false;
  }

  public boolean selectFrame(int indexToSelect) {
    if (getSelectedIndex() != indexToSelect &&
        getElementCount() > indexToSelect &&
        getModel().getElementAt(indexToSelect) != null) {
      setSelectedIndex(indexToSelect);
      return true;
    }
    return false;
  }

  public @Nullable XStackFrame getSelectedFrame() {
    Object value = getSelectedValue();
    return value instanceof XStackFrame ? (XStackFrame)value : null;
  }

  @Override
  protected @Nullable Navigatable getSelectedFrameNavigatable() {
    XStackFrame frame = getSelectedFrame();
    Navigatable navigatable = frame != null ? getFrameNavigatable(frame) : null;
    if (navigatable instanceof OpenFileDescriptor) {
      ((OpenFileDescriptor)navigatable).setUsePreviewTab(true);
    }
    return navigatable != null ? keepFocus(navigatable) : null;
  }

  /**
   * Forbids focus requests unless the editor area is already focused.
   */
  private @NotNull Navigatable keepFocus(@NotNull Navigatable navigatable) {
    FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(myProject);
    boolean isEditorAreaFocused = IJSwingUtilities.hasFocus(fileEditorManager.getComponent());
    return isEditorAreaFocused ? navigatable : new Navigatable() {
      @Override
      public void navigate(boolean requestFocus) {
        navigatable.navigate(false);
      }

      @Override
      public boolean canNavigate() { return navigatable.canNavigate(); }

      @Override
      public boolean canNavigateToSource() { return navigatable.canNavigateToSource(); }
    };
  }

  private @Nullable Navigatable getFrameNavigatable(@NotNull XStackFrame frame) {
    XSourcePosition position = frame.getSourcePosition();
    return position != null ? position.createNavigatable(myProject) : null;
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
        if (hasSeparator(value, index)) {
          setSeparatorFont(list.getFont());
        }
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
    private final XDebuggerPopFrameIcon myPopFrameIcon = JBUIScale.scaleIcon(
      new XDebuggerPopFrameIcon(AllIcons.Actions.InlineDropFrame, AllIcons.Actions.InlineDropFrameSelected, 16, 16)
    );

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

      var loc = XDebuggerFrameListMouseListener.getMouseLocation(list);
      var bounds = XDebuggerFrameListMouseListener.getHoveredCellBounds(list);
      var hoveredIndex = XDebuggerFrameListMouseListener.getHoveredIndex(list);
      var iconHovered = isIconHovered(loc, bounds);
      var hovered = index == hoveredIndex;
      boolean canDropSomething = XDebuggerFrameListMouseListener.getCanDropFrame(list);
      if (!selected) {
        Color c = getFrameBgColor(stackFrame);
        if (index <= hoveredIndex && canDropSomething && iconHovered) {
          setBackground(RenderingUtil.getHoverBackground(list));
        }
        else if (c != null) {
          setBackground(c);
        }
      }
      else {
        setBackground(UIUtil.getListSelectionBackground(hasFocus));
        setForeground(UIUtil.getListSelectionForeground(hasFocus));
        mySelectionForeground = getForeground();
      }

      stackFrame.customizePresentation(this);

      // override icon which is set by customizePresentation if needed
      if ((hovered && canDropSomething)
          || (selected && hoveredIndex < 0 && canDropSelectedFrame(list)))
      {
        setIcon(myPopFrameIcon);
        if (iconHovered && selected) {
          myPopFrameIcon.setBackground(ColorUtil.withAlpha(UIUtil.getListSelectionForeground(true), 0.2));
        } else {
          myPopFrameIcon.setBackground(null);
        }
        myPopFrameIcon.setSelected(selected && hasFocus);
      }
    }

    private boolean canDropSelectedFrame(JList list) {
      if (!(list instanceof XDebuggerFramesList)) {
        return false;
      }
      var selectedValue = list.getSelectedValue();
      if (!(selectedValue instanceof XStackFrame)) {
        return false;
      }
      try {
        var dropFrameHandler = findDropFrameHandler((XDebuggerFramesList)list);
        if (dropFrameHandler == null) {
          return false;
        }
        return dropFrameHandler.canDrop((XStackFrame)selectedValue);
      } catch (Throwable ignore) {
      }
      return false;
    }

    public boolean isIconHovered(@Nullable Point p, @Nullable Rectangle bounds) {
      if (p == null || bounds == null) {
        return false;
      }
      var leftPadding = this.getIpad().left;
      var iconWidth = myPopFrameIcon.getIconWidth();
      return new Rectangle(leftPadding, 0, iconWidth, bounds.height).contains(p);
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
    @NlsContexts.Separator String getCaptionAboveOf();
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

  /**
   * Watches mouse events and updates some data like:
   * <ol>
   *   <li>Hover index of the row under the mouse cursor</li>
   *   <li>Hover position of the mouse cursor relative to the hovered row</li>
   *   <li>Hovered row bounds</li>
   *   <li>Flag if hovered frame can be dropped</li>
   * </ol>
   *
   * @see XDropFrameHandler#canDrop(XStackFrame)
   * @see #findDropFrameHandler(XDebuggerFramesList)
   */
  private static final class XDebuggerFrameListMouseListener extends HoverListener {
    private static final Key<Point> HOVER_POSITION = Key.create("XDEBUGGER_HOVER_POSITION");
    private static final Key<Integer> HOVER_INDEX = Key.create("XDEBUGGER_HOVER_INDEX");
    private static final Key<Rectangle> HOVER_BOUNDS = Key.create("XDEBUGGER_HOVER_BOUNDS");
    private static final Key<Boolean> CAN_DROP_FRAME = Key.create("XDEBUGGER_CAN_DROP_FRAME");
    private static final Key<Boolean> RESET_ICON_HOVERED = Key.create("XDEBUGGER_RESET_ICON_HOVERERD");

    /**
     * Installs listeners to the {@link XDebuggerFramesList}.
     *
     * For a mouse click event it tries to find {@link XDebuggerFrameListRenderer}
     * and call {@link XDebuggerFrameListRenderer#onMouseEvent(MouseEvent, int)}.
     *
     * @param list
     */
    private void installListeners(@NotNull XDebuggerFramesList list) {
      addTo(list);

      var helpTooltip = new HelpTooltip()
        .setTitle(XDebuggerBundle.message("debugger.frame.list.help.title"))
        .setDescription(XDebuggerBundle.message("debugger.frame.list.help.description"));
      Shortcut[] shortcuts = CommonShortcuts.getDelete().getShortcuts();
      if (shortcuts.length > 0) {
        helpTooltip.setShortcut(shortcuts[0]);
      }
      helpTooltip.installOn(list);
      HelpTooltip.setMasterPopupOpenCondition(list, () -> {
        return ClientProperty.isTrue(list, RESET_ICON_HOVERED);
      });
    }

    public static @Nullable Point getMouseLocation(@NotNull Component component) {
      return ClientProperty.get(component, HOVER_POSITION);
    }

    public static int getHoveredIndex(@NotNull Component component) {
      return Optional.ofNullable(ClientProperty.get(component, HOVER_INDEX)).orElse(-1);
    }

    public static @Nullable Rectangle getHoveredCellBounds(@NotNull Component component) {
      return ClientProperty.get(component, HOVER_BOUNDS);
    }

    public static boolean getCanDropFrame(@NotNull Component component) {
      return ClientProperty.isTrue(component, CAN_DROP_FRAME);
    }

    public static boolean getResetIconHovered(@NotNull Component component) {
      return ClientProperty.isTrue(component, RESET_ICON_HOVERED);
    }

    private static void updateHover(@NotNull Component component, @Nullable Point point) {
      if (!(component instanceof XDebuggerFramesList)) return;
      var list = ((XDebuggerFramesList)component);
      var oldHoverIndex = getHoveredIndex(component);
      ClientProperty.put(list, HOVER_POSITION, null);
      ClientProperty.put(list, HOVER_BOUNDS, null);
      ClientProperty.put(list, HOVER_INDEX, -1);
      ClientProperty.put(list, CAN_DROP_FRAME, false);
      ClientProperty.put(list, RESET_ICON_HOVERED, false);
      boolean resetHoverActions = true;

      if (point != null) {
        var renderer = getFrameListRenderer(list, point);
        var index = list.locationToIndex(point);
        var bounds = list.getCellBounds(index, index);
        var pointInCellBounds = bounds == null ? point : new Point(point.x - bounds.x, point.y - bounds.y);
        if (oldHoverIndex != index) {
          HelpTooltip.hide(list);
        }
        if (bounds != null) {
          ClientProperty.put(list, HOVER_POSITION, pointInCellBounds);
          ClientProperty.put(list, HOVER_BOUNDS, bounds);
        }
        ClientProperty.put(list, HOVER_INDEX, index);
        if (index >= 0 && index < list.getModel().getSize()) {
          var value = list.getModel().getElementAt(index);
          if (value instanceof XStackFrame) {
            var dropFrameHandler = findDropFrameHandler(list);
            ClientProperty.put(list, CAN_DROP_FRAME, dropFrameHandler != null && dropFrameHandler.canDrop((XStackFrame)value));
          }
        }
        if (renderer != null && getCanDropFrame(list)) {
          boolean isHovered = renderer.isIconHovered(pointInCellBounds, bounds);
          if (isHovered) {
            list.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            ClientProperty.put(list, RESET_ICON_HOVERED, true);
            resetHoverActions = false;
          }
        }
      }

      if (resetHoverActions) {
        list.setCursor(Cursor.getDefaultCursor());
        HelpTooltip.hide(list);
      }

      component.repaint();
    }

    @Override
    public void mouseEntered(@NotNull Component component, int x, int y) {
      updateHover(component, new Point(x, y));
    }

    @Override
    public void mouseMoved(@NotNull Component component, int x, int y) {
      updateHover(component, new Point(x, y));
    }

    @Override
    public void mouseExited(@NotNull Component component) {
      updateHover(component, null);
    }

    private static @Nullable XDebuggerFrameListRenderer getFrameListRenderer(@NotNull JList list, @NotNull Point mouseLocation) {
      var renderer = list.getCellRenderer();
      var index = list.locationToIndex(mouseLocation);
      if (index < 0 || index >= list.getModel().getSize()) {
        return null;
      }
      var value = list.getModel().getElementAt(index);
      @SuppressWarnings("unchecked")
      var component = ObjectUtils.tryCast(
        renderer.getListCellRendererComponent(list, value, index, false, false),
        JComponent.class
      );
      if (component == null) {
        return null;
      }
      return ContainerUtil.getFirstItem(
        ComponentUtil.findComponentsOfType(component, XDebuggerFrameListRenderer.class)
      );
    }
  }

  private static class XDebuggerPopFrameIcon extends JBScalableIcon {

    private final @NotNull Icon myIcon;
    private final @Nullable Icon mySelectedIcon;
    private @Nullable Color myBackground = null;
    private boolean isSelected = false;
    private final int myIconWidth;
    private final int myIconHeight;

    XDebuggerPopFrameIcon(@NotNull Icon icon, @Nullable Icon selectedIcon, int width, int height) {
      myIcon = icon;
      mySelectedIcon = selectedIcon;
      myIconWidth = width;
      myIconHeight = height;
    }

    private @Nullable Color getBackground() {
      return myBackground;
    }

    private void setBackground(@Nullable Color background) {
      myBackground = background;
    }

    private boolean isSelected() {
      return isSelected;
    }

    private void setSelected(boolean selected) {
      isSelected = selected;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Color background = getBackground();
      if (background != null) {
        if (g instanceof Graphics2D) {
          ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        g.setColor(background);
        int arc = (int)Math.ceil(scaleVal(5));
        g.fillRoundRect(x, y, getIconWidth(), getIconHeight(), arc, arc);
      }
      var icon = (!isSelected() || mySelectedIcon == null) ? myIcon : mySelectedIcon;
      icon.paintIcon(c, g, x + (getIconWidth() - icon.getIconWidth()) / 2, y + (getIconHeight() - icon.getIconHeight()) / 2);
    }

    @Override
    public int getIconWidth() {
      return (int)Math.ceil(scaleVal(myIconWidth));
    }

    @Override
    public int getIconHeight() {
      return (int)Math.ceil(scaleVal(myIconHeight));
    }
  }

  private static @Nullable XDropFrameHandler findDropFrameHandler(XDebuggerFramesList list) {
    var session = DataManager.getInstance().getDataContext(list).getData(XDebugSession.DATA_KEY);
    if (session == null) {
      return null;
    }
    return session.getDebugProcess().getDropFrameHandler();
  }

  private static class ResetFrameAction extends DumbAwareAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      var inputEvent = e.getInputEvent();
      var list = e.getData(FRAMES_LIST);
      if (inputEvent == null || list == null) {
        return;
      }
      var index = -1;
      var event = ObjectUtils.tryCast(inputEvent, MouseEvent.class);
      if (event != null) {
        if (UIUtil.isActionClick(event, MouseEvent.MOUSE_PRESSED) && XDebuggerFrameListMouseListener.getResetIconHovered(list)) {
          index = XDebuggerFrameListMouseListener.getHoveredIndex(list);
        }
      }
      else {
        index = list.getSelectedIndex();
      }
      var model = list.getModel();
      if (index >= 0 && index < model.getSize()) {
        var handler = findDropFrameHandler(list);
        var frame = ObjectUtils.tryCast(model.getElementAt(index), XStackFrame.class);
        if (frame != null && handler != null && handler.canDrop(frame)) {
          handler.drop(frame);
          inputEvent.consume();
        }
      }
    }
  }
}