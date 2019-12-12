// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.*;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.*;
import com.intellij.ui.ComponentWithMnemonics;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.impl.ContentManagerImpl;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Map;

/**
 * @author Eugene Belyaev
 * @author Vladimir Kondratyev
 */
public final class InternalDecorator extends JPanel implements Queryable, DataProvider, ComponentWithMnemonics {
  private WindowInfoImpl myInfo;
  private final ToolWindowImpl toolWindow;
  private final MyDivider myDivider;
  private final RemoveStripeButtonAction myRemoveFromSideBarAction;

  private ActionGroup myAdditionalGearActions;
  /**
   * Catches all event from tool window and modifies decorator's appearance.
   */
  static final String HIDE_ACTIVE_WINDOW_ACTION_ID = "HideActiveWindow";

  //See ToolWindowViewModeAction and ToolWindowMoveAction
  @NonNls @Deprecated public static final String TOGGLE_PINNED_MODE_ACTION_ID = "TogglePinnedMode";
  @NonNls @Deprecated public static final String TOGGLE_DOCK_MODE_ACTION_ID = "ToggleDockMode";
  @NonNls @Deprecated public static final String TOGGLE_FLOATING_MODE_ACTION_ID = "ToggleFloatingMode";
  @NonNls @Deprecated public static final String TOGGLE_WINDOWED_MODE_ACTION_ID = "ToggleWindowedMode";
  @NonNls @Deprecated public static final String TOGGLE_SIDE_MODE_ACTION_ID = "ToggleSideMode";

  private static final String TOGGLE_CONTENT_UI_TYPE_ACTION_ID = "ToggleContentUiTypeMode";

  private final ToolWindowHeader myHeader;
  private final ActionGroup myToggleToolbarGroup;
  private final JPanel contentPane;

  InternalDecorator(@NotNull WindowInfoImpl info, @NotNull ToolWindowImpl toolWindow) {
    super(new BorderLayout());

    this.toolWindow = toolWindow;
    toolWindow.setDecorator(this);
    myDivider = new MyDivider();

    /*
     * Actions
     */
    ToggleContentUiTypeAction toggleContentUiTypeAction = new ToggleContentUiTypeAction();
    myRemoveFromSideBarAction = new RemoveStripeButtonAction();
    myToggleToolbarGroup = ToggleToolbarAction.createToggleToolbarGroup(toolWindow.getToolWindowManager().getProject(), this.toolWindow);
    if (!ToolWindowId.PREVIEW.equals(info.getId())) {
      ((DefaultActionGroup)myToggleToolbarGroup).addAction(toggleContentUiTypeAction);
    }

    setFocusable(false);
    setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());

    myHeader = new ToolWindowHeader(toolWindow, () -> createPopupGroup(true)) {
      @Override
      protected boolean isActive() {
        return InternalDecorator.this.toolWindow.isActive();
      }

      @Override
      protected void hideToolWindow() {
        fireHidden();
      }
    };

    enableEvents(AWTEvent.COMPONENT_EVENT_MASK);

    contentPane = new JPanel(new BorderLayout());
    installFocusTraversalPolicy(contentPane, new LayoutFocusTraversalPolicy());
    contentPane.add(myHeader, BorderLayout.NORTH);

    add(contentPane, BorderLayout.CENTER);
    if (SystemInfo.isMac) {
      setBackground(new JBColor(Gray._200, Gray._90));
    }

    setWindowInfo(info);
  }

  @Override
  public String toString() {
    return toolWindow.getId();
  }

  public boolean isFocused() {
    IdeFocusManager focusManager = toolWindow.getToolWindowManager().getFocusManager();
    Component component = focusManager.getFocusedDescendantFor(toolWindow.getComponent());
    if (component != null) {
      return true;
    }

    Component owner = focusManager.getLastFocusedFor(WindowManager.getInstance().getIdeFrame(toolWindow.getToolWindowManager().getProject()));
    return owner != null && SwingUtilities.isDescendingFrom(owner, toolWindow.getComponent());
  }

  /**
   * Applies specified decoration.
   */
  public final void apply(@NotNull WindowInfoImpl info) {
    if (myInfo.equals(info) || toolWindow.getToolWindowManager().getProject().isDisposed()) {
      return;
    }

    setWindowInfo(info);
  }

  private void setWindowInfo(@NotNull WindowInfoImpl info) {
    myInfo = info;

    // Anchor
    ToolWindowAnchor anchor = myInfo.getAnchor();
    if (info.isSliding()) {
      myDivider.invalidate();
      if (ToolWindowAnchor.TOP == anchor) {
        add(myDivider, BorderLayout.SOUTH);
      }
      else if (ToolWindowAnchor.LEFT == anchor) {
        add(myDivider, BorderLayout.EAST);
      }
      else if (ToolWindowAnchor.BOTTOM == anchor) {
        add(myDivider, BorderLayout.NORTH);
      }
      else if (ToolWindowAnchor.RIGHT == anchor) {
        add(myDivider, BorderLayout.WEST);
      }
      myDivider.setPreferredSize(new Dimension(0, 0));
    }
    else {
      // docked and floating windows don't have divider
      remove(myDivider);
    }

    validate();
    repaint();

    // push "apply" request forward
    if (myInfo.isFloating() && myInfo.isVisible()) {
      FloatingDecorator floatingDecorator = (FloatingDecorator)SwingUtilities.getAncestorOfClass(FloatingDecorator.class, this);
      if (floatingDecorator != null) {
        floatingDecorator.apply(myInfo);
      }
    }

    toolWindow.getContentUI().setType(myInfo.getContentUiType());
    setBorder(new InnerPanelBorder(toolWindow, myInfo));
  }

  @Nullable
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (PlatformDataKeys.TOOL_WINDOW.is(dataId)) {
      return toolWindow;
    }
    return null;
  }

  /**
   * Fires event that "hide" button has been pressed.
   */
  final void fireHidden() {
    toolWindow.getToolWindowManager().hideToolWindow(toolWindow.getId(), false);
  }

  /**
   * Fires event that "hide" button has been pressed.
   */
  final void fireHiddenSide() {
    toolWindow.getToolWindowManager().hideToolWindow(toolWindow.getId(), true);
  }

  /**
   * Fires event that user performed click into the title bar area.
   */
  final void fireActivated() {
    toolWindow.getToolWindowManager().activated(this);
  }

  final void fireResized() {
    toolWindow.getToolWindowManager().resized(this);
  }

  private void fireContentUiTypeChanges(@NotNull ToolWindowContentUiType type) {
    toolWindow.getToolWindowManager().setContentUiType(toolWindow.getId(), type);
  }

  void addContentComponent(boolean dumbAware, @NotNull ContentManagerImpl contentManager) {
    JPanel innerPanel = new JPanel(new BorderLayout());
    JComponent toolWindowComponent = contentManager.getComponent();
    if (!dumbAware) {
      toolWindowComponent = DumbService.getInstance(toolWindow.getToolWindowManager().getProject()).wrapGently(toolWindowComponent, toolWindow.getDisposable());
    }
    innerPanel.add(toolWindowComponent, BorderLayout.CENTER);

    contentPane.add(new NonOpaquePanel(innerPanel), BorderLayout.CENTER);
  }

  @Override
  protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
    if (condition == WHEN_ANCESTOR_OF_FOCUSED_COMPONENT && pressed) {
      Collection<KeyStroke> keyStrokes = KeymapUtil.getKeyStrokes(ActionManager.getInstance().getAction("FocusEditor").getShortcutSet());
      if (keyStrokes.contains(ks)) {
        toolWindow.getToolWindowManager().activateEditorComponent();
        return true;
      }
    }
    return super.processKeyBinding(ks, e, condition, pressed);
  }

  public void setTitleActions(@NotNull AnAction[] actions) {
    myHeader.setAdditionalTitleActions(actions);
  }

  void setTabActions(@NotNull AnAction[] actions) {
    myHeader.setTabActions(actions);
  }

  private static final class InnerPanelBorder implements Border {
    @NotNull
    private final ToolWindowImpl window;
    @NotNull
    private final WindowInfo windowInfo;

    private InnerPanelBorder(@NotNull ToolWindowImpl window, @NotNull WindowInfo windowInfo) {
      this.window = window;
      this.windowInfo = windowInfo;
    }

    @Override
    public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
      g.setColor(JBColor.border());
      doPaintBorder(c, g, x, y, width, height);
    }

    private void doPaintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      Insets insets = getBorderInsets(c);

      Graphics2D graphics2D = (Graphics2D)g;
      if (insets.top > 0) {
        LinePainter2D.paint(graphics2D, x, y + insets.top - 1, x + width - 1, y + insets.top - 1);
        LinePainter2D.paint(graphics2D, x, y + insets.top, x + width - 1, y + insets.top);
      }

      if (insets.left > 0) {
        LinePainter2D.paint(graphics2D, x, y, x, y + height);
        LinePainter2D.paint(graphics2D, x + 1, y, x + 1, y + height);
      }

      if (insets.right > 0) {
        LinePainter2D.paint(graphics2D, x + width - 1, y + insets.top, x + width - 1, y + height);
        LinePainter2D.paint(graphics2D, x + width, y + insets.top, x + width, y + height);
      }

      if (insets.bottom > 0) {
        LinePainter2D.paint(graphics2D, x, y + height - 1, x + width, y + height - 1);
        LinePainter2D.paint(graphics2D, x, y + height, x + width, y + height);
      }
    }

    @Override
    public Insets getBorderInsets(@NotNull Component c) {
      ToolWindowManagerImpl toolWindowManager = window.getToolWindowManager();
      if (toolWindowManager.getProject().isDisposed() ||
          !toolWindowManager.isToolWindowRegistered(window.getId()) || windowInfo.getType() == ToolWindowType.FLOATING || windowInfo.getType() == ToolWindowType.WINDOWED) {
        return JBUI.emptyInsets();
      }

      ToolWindowAnchor anchor = windowInfo.getAnchor();
      Component component = window.getComponent();
      Container parent = component.getParent();
      boolean isSplitter = false;
      boolean isFirstInSplitter = false;
      boolean isVerticalSplitter = false;
      while(parent != null) {
        if (parent instanceof Splitter) {
          Splitter splitter = (Splitter)parent;
          isSplitter = true;
          isFirstInSplitter = splitter.getFirstComponent() == component;
          isVerticalSplitter = splitter.isVertical();
          break;
        }
        component = parent;
        parent = component.getParent();
      }

      int top = isSplitter && (anchor == ToolWindowAnchor.RIGHT || anchor == ToolWindowAnchor.LEFT) && windowInfo.isSplit() && isVerticalSplitter ? -1 : 0;
      int left = anchor == ToolWindowAnchor.RIGHT && (!isSplitter || isVerticalSplitter || isFirstInSplitter) ? 1 : 0;
      int bottom = 0;
      int right = anchor == ToolWindowAnchor.LEFT && (!isSplitter || isVerticalSplitter || !isFirstInSplitter) ? 1 : 0;
      return new Insets(top, left, bottom, right);
    }

    @Override
    public boolean isBorderOpaque() {
      return false;
    }
  }

  @NotNull
  final ActionGroup createPopupGroup() {
    return createPopupGroup(false);
  }

  @NotNull
  private ActionGroup createPopupGroup(boolean skipHideAction) {
    final DefaultActionGroup group = new GearActionGroup();
    if (myInfo == null) {
      return group;
    }

    if (!skipHideAction) {
      group.addSeparator();
      group.add(new HideAction());
    }

    group.addSeparator();
    group.add(new ContextHelpAction() {
      @Nullable
      @Override
      protected String getHelpId(DataContext dataContext) {
        Content content = toolWindow.getContentManager().getSelectedContent();
        if (content != null) {
          String helpId = content.getHelpId();
          if (helpId != null) {
            return helpId;
          }
        }

        String id = toolWindow.getHelpId();
        if (id != null) {
          return id;
        }

        DataContext context = content != null ? DataManager.getInstance().getDataContext(content.getComponent()) : dataContext;
        return super.getHelpId(context);
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabledAndVisible(getHelpId(e.getDataContext()) != null);
      }
    });
    return group;
  }

  @NotNull
  private DefaultActionGroup createResizeActionGroup() {
    DefaultActionGroup resize = new DefaultActionGroup(ActionsBundle.groupText("ResizeToolWindowGroup"), true) {
      @Override
      public boolean isDumbAware() {
        return true;
      }
    };
    resize.add(new ResizeToolWindowAction.Left(toolWindow, this));
    resize.add(new ResizeToolWindowAction.Right(toolWindow, this));
    resize.add(new ResizeToolWindowAction.Up(toolWindow, this));
    resize.add(new ResizeToolWindowAction.Down(toolWindow, this));
    resize.add(ActionManager.getInstance().getAction("MaximizeToolWindow"));
    return resize;
  }

  private class GearActionGroup extends DefaultActionGroup {
    GearActionGroup() {
      getTemplatePresentation().setIcon(AllIcons.General.GearPlain);
      getTemplatePresentation().setText("Show Options Menu");
      if (myInfo == null) return;

      if (myAdditionalGearActions != null) {
        if (myAdditionalGearActions.isPopup() && !StringUtil.isEmpty(myAdditionalGearActions.getTemplatePresentation().getText())) {
          add(myAdditionalGearActions);
        } else {
          addSorted(this, myAdditionalGearActions);
        }
        addSeparator();
      }

      addAction(myToggleToolbarGroup).setAsSecondary(true);
      addSeparator();

      add(new ToolWindowViewModeAction.Group());
      add(new ToolWindowMoveAction.Group());
      add(createResizeActionGroup());
      addSeparator();

      add(myRemoveFromSideBarAction);
    }
  }

  private static void addSorted(DefaultActionGroup main, ActionGroup group) {
    final AnAction[] children = group.getChildren(null);
    boolean hadSecondary = false;
    for (AnAction action : children) {
      if (group.isPrimary(action)) {
        main.add(action);
      } else {
        hadSecondary = true;
      }
    }
    if (hadSecondary) {
      main.addSeparator();
      for (AnAction action : children) {
        if (!group.isPrimary(action)) {
          main.addAction(action).setAsSecondary(true);
        }
      }
    }
    String separatorText = group.getTemplatePresentation().getText();
    if (children.length > 0 && !StringUtil.isEmpty(separatorText)) {
      main.addAction(new Separator(separatorText), Constraints.FIRST);
    }
  }

  /**
   * @return tool window associated with the decorator.
   */
  @NotNull
  final ToolWindowImpl getToolWindow() {
    return toolWindow;
  }

  /**
   * @return last window info applied to the decorator.
   */
  @NotNull
  final WindowInfoImpl getWindowInfo() {
    return myInfo;
  }

  public int getHeaderHeight() {
    return myHeader.getPreferredSize().height;
  }

  public void setHeaderVisible(boolean value) {
    myHeader.setVisible(value);
  }

  @Override
  protected final void processComponentEvent(final ComponentEvent e) {
    super.processComponentEvent(e);
    if (ComponentEvent.COMPONENT_RESIZED == e.getID()) {
      fireResized();
    }
  }

  void removeStripeButton() {
    toolWindow.getToolWindowManager().setShowStripeButton(toolWindow.getId(), false);
    fireHidden();
  }

  private final class RemoveStripeButtonAction extends AnAction implements DumbAware {
    RemoveStripeButtonAction() {
      Presentation presentation = getTemplatePresentation();
      presentation.setText(ActionsBundle.message("action.RemoveStripeButton.text"));
      presentation.setDescription(ActionsBundle.message("action.RemoveStripeButton.description"));
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(myInfo.isShowStripeButton());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      removeStripeButton();
    }
  }

  private final class HideAction extends AnAction implements DumbAware {
    HideAction() {
      ActionUtil.copyFrom(this, HIDE_ACTIVE_WINDOW_ACTION_ID);
      getTemplatePresentation().setText(UIBundle.message("tool.window.hide.action.name"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      fireHidden();
    }

    @Override
    public void update(@NotNull final AnActionEvent event) {
      Presentation presentation = event.getPresentation();
      presentation.setEnabled(myInfo.isVisible());
    }
  }

  private final class ToggleContentUiTypeAction extends ToggleAction implements DumbAware {
    private boolean myHadSeveralContents;

    private ToggleContentUiTypeAction() {
      ActionUtil.copyFrom(this, TOGGLE_CONTENT_UI_TYPE_ACTION_ID);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      myHadSeveralContents = myHadSeveralContents || toolWindow.getContentManager().getContentCount() > 1;
      super.update(e);
      e.getPresentation().setVisible(myHadSeveralContents);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myInfo.getContentUiType() == ToolWindowContentUiType.COMBO;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      fireContentUiTypeChanges(state ? ToolWindowContentUiType.COMBO : ToolWindowContentUiType.TABBED);
    }
  }

  private final class MyDivider extends JPanel {
    private boolean myDragging;
    private Disposable myDisposable;
    private IdeGlassPane myGlassPane;

    private final MouseAdapter myListener = new MyMouseAdapter();

    @Override
    public void addNotify() {
      super.addNotify();
      myGlassPane = IdeGlassPaneUtil.find(this);
      myDisposable = Disposer.newDisposable();
      myGlassPane.addMouseMotionPreprocessor(myListener, myDisposable);
      myGlassPane.addMousePreprocessor(myListener, myDisposable);
    }

    @Override
    public void removeNotify() {
      super.removeNotify();
      if (myDisposable != null && !Disposer.isDisposed(myDisposable)) {
        Disposer.dispose(myDisposable);
      }
    }

    boolean isInDragZone(MouseEvent e) {
      final Point p = SwingUtilities.convertMouseEvent(e.getComponent(), e, this).getPoint();
      return Math.abs(myInfo.getAnchor().isHorizontal() ? p.y : p.x) < 6;
    }

    private final class MyMouseAdapter extends MouseAdapter {
      private void updateCursor(MouseEvent e) {
        if (isInDragZone(e)) {
          myGlassPane.setCursor(MyDivider.this.getCursor(), MyDivider.this);
          e.consume();
        }
      }

      @Override
      public void mousePressed(MouseEvent e) {
        myDragging = isInDragZone(e);
        updateCursor(e);
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        updateCursor(e);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        updateCursor(e);
        myDragging = false;
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        updateCursor(e);
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        if (!myDragging) {
          return;
        }

        MouseEvent event = SwingUtilities.convertMouseEvent(e.getComponent(), e, MyDivider.this);
        final ToolWindowAnchor anchor = myInfo.getAnchor();
        final Point point = event.getPoint();
        final Container windowPane = InternalDecorator.this.getParent();
        Point lastPoint = SwingUtilities.convertPoint(MyDivider.this, point, windowPane);
        lastPoint.x = Math.min(Math.max(lastPoint.x, 0), windowPane.getWidth());
        lastPoint.y = Math.min(Math.max(lastPoint.y, 0), windowPane.getHeight());

        final Rectangle bounds = InternalDecorator.this.getBounds();
        if (anchor == ToolWindowAnchor.TOP) {
          InternalDecorator.this.setBounds(0, 0, bounds.width, lastPoint.y);
        }
        else if (anchor == ToolWindowAnchor.LEFT) {
          InternalDecorator.this.setBounds(0, 0, lastPoint.x, bounds.height);
        }
        else if (anchor == ToolWindowAnchor.BOTTOM) {
          InternalDecorator.this.setBounds(0, lastPoint.y, bounds.width, windowPane.getHeight() - lastPoint.y);
        }
        else if (anchor == ToolWindowAnchor.RIGHT) {
          InternalDecorator.this.setBounds(lastPoint.x, 0, windowPane.getWidth() - lastPoint.x, bounds.height);
        }
        InternalDecorator.this.validate();
        e.consume();
      }
    }

    @NotNull
    @Override
    public Cursor getCursor() {
      final boolean isVerticalCursor = myInfo.isDocked() ? myInfo.getAnchor().isSplitVertically() : myInfo.getAnchor().isHorizontal();
      return isVerticalCursor ? Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR) : Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
    }
  }

  @Override
  public void putInfo(@NotNull Map<String, String> info) {
    info.put("toolWindowTitle", toolWindow.getTitle());

    final Content selection = toolWindow.getContentManager().getSelectedContent();
    if (selection != null) {
      info.put("toolWindowTab", selection.getTabName());
    }
  }

  public void setAdditionalGearActions(@Nullable ActionGroup additionalGearActions) {
    myAdditionalGearActions = additionalGearActions;
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleInternalDecorator();
    }
    return accessibleContext;
  }

  protected class AccessibleInternalDecorator extends AccessibleJPanel {
    @Override
    public String getAccessibleName() {
      String name = super.getAccessibleName();
      if (name == null) {
        String title = StringUtil.defaultIfEmpty(toolWindow.getTitle(), toolWindow.getStripeTitle());
        title = StringUtil.defaultIfEmpty(title, toolWindow.getId());
        name = StringUtil.notNullize(title) + " Tool Window";
      }
      return name;
    }
  }

  /**
   * Installs a focus traversal policy for the tool window.
   * If the policy cannot handle a keystroke, it delegates the handling to
   * the nearest ancestors focus traversal policy. For instance,
   * this policy does not handle KeyEvent.VK_ESCAPE, so it can delegate the handling
   * to a ThreeComponentSplitter instance.
   */
  static void installFocusTraversalPolicy(@NotNull Container container, @NotNull FocusTraversalPolicy policy) {
    container.setFocusCycleRoot(true);
    container.setFocusTraversalPolicyProvider(true);
    container.setFocusTraversalPolicy(policy);
    installDefaultFocusTraversalKeys(container, KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS);
    installDefaultFocusTraversalKeys(container, KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS);
  }

  private static void installDefaultFocusTraversalKeys(@NotNull Container container, int id) {
    container.setFocusTraversalKeys(id, KeyboardFocusManager.getCurrentKeyboardFocusManager().getDefaultFocusTraversalKeys(id));
  }
}
