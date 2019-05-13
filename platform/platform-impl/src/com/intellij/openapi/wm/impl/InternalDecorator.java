// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.*;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.*;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.content.Content;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.*;
import java.awt.event.*;
import java.util.Map;

/**
 * @author Eugene Belyaev
 * @author Vladimir Kondratyev
 */
public final class InternalDecorator extends JPanel implements Queryable, DataProvider {

  private Project myProject;
  private WindowInfoImpl myInfo;
  private final ToolWindowImpl myToolWindow;
  private final MyDivider myDivider;
  private final EventDispatcher<InternalDecoratorListener> myDispatcher = EventDispatcher.create(InternalDecoratorListener.class);
  /*
   * Actions
   */
  private final ToggleContentUiTypeAction myToggleContentUiTypeAction;
  private final RemoveStripeButtonAction myRemoveFromSideBarAction;

  private ActionGroup myAdditionalGearActions;
  /**
   * Catches all event from tool window and modifies decorator's appearance.
   */
  @NonNls static final String HIDE_ACTIVE_WINDOW_ACTION_ID = "HideActiveWindow";

  //See ToolWindowViewModeAction and ToolWindowMoveAction
  @NonNls @Deprecated public static final String TOGGLE_PINNED_MODE_ACTION_ID = "TogglePinnedMode";
  @NonNls @Deprecated public static final String TOGGLE_DOCK_MODE_ACTION_ID = "ToggleDockMode";
  @NonNls @Deprecated public static final String TOGGLE_FLOATING_MODE_ACTION_ID = "ToggleFloatingMode";
  @NonNls @Deprecated public static final String TOGGLE_WINDOWED_MODE_ACTION_ID = "ToggleWindowedMode";
  @NonNls @Deprecated public static final String TOGGLE_SIDE_MODE_ACTION_ID = "ToggleSideMode";

  @NonNls private static final String TOGGLE_CONTENT_UI_TYPE_ACTION_ID = "ToggleContentUiTypeMode";

  private ToolWindowHeader myHeader;
  private final ActionGroup myToggleToolbarGroup;

  InternalDecorator(final Project project, @NotNull WindowInfoImpl info, final ToolWindowImpl toolWindow, boolean dumbAware) {
    super(new BorderLayout());
    myProject = project;
    myToolWindow = toolWindow;
    myToolWindow.setDecorator(this);
    myDivider = new MyDivider();

    myToggleContentUiTypeAction = new ToggleContentUiTypeAction();
    myRemoveFromSideBarAction = new RemoveStripeButtonAction();
    myToggleToolbarGroup = ToggleToolbarAction.createToggleToolbarGroup(myProject, myToolWindow);
    if (!ToolWindowId.PREVIEW.equals(info.getId())) {
      ((DefaultActionGroup)myToggleToolbarGroup).addAction(myToggleContentUiTypeAction);
    }

    setFocusable(false);
    setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());

    myHeader = new ToolWindowHeader(toolWindow, () -> createPopupGroup(true)) {
      @Override
      protected boolean isActive() {
        return myToolWindow.isActive();
      }

      @Override
      protected void hideToolWindow() {
        fireHidden();
      }
    };

    init(dumbAware);

    apply(info);
  }

  @Override
  public String toString() {
    return myToolWindow.getId();
  }

  public boolean isFocused() {
    IdeFocusManager fm = IdeFocusManager.getInstance(myProject);
    Component component = fm.getFocusedDescendantFor(myToolWindow.getComponent());
    if (component != null) return true;

    Component owner = fm.getLastFocusedFor(WindowManager.getInstance().getIdeFrame(myProject));

    return owner != null && SwingUtilities.isDescendingFrom(owner, myToolWindow.getComponent());
  }

  /**
   * Applies specified decoration.
   */
  public final void apply(@NotNull WindowInfoImpl info) {
    if (Comparing.equal(myInfo, info) || myProject == null || myProject.isDisposed()) {
      return;
    }
    myInfo = info;

    // Anchor
    final ToolWindowAnchor anchor = myInfo.getAnchor();
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
    else { // docked and floating windows don't have divider
      remove(myDivider);
    }

    validate();
    repaint();


    // Push "apply" request forward

    if (myInfo.isFloating() && myInfo.isVisible()) {
      final FloatingDecorator floatingDecorator = (FloatingDecorator)SwingUtilities.getAncestorOfClass(FloatingDecorator.class, this);
      if (floatingDecorator != null) {
        floatingDecorator.apply(myInfo);
      }
    }

    myToolWindow.getContentUI().setType(myInfo.getContentUiType());
    setBorder(new InnerPanelBorder(myToolWindow));
  }

  @Nullable
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (PlatformDataKeys.TOOL_WINDOW.is(dataId)) {
      return myToolWindow;
    }
    return null;
  }

  final void addInternalDecoratorListener(InternalDecoratorListener l) {
    myDispatcher.addListener(l);
  }

  final void removeInternalDecoratorListener(InternalDecoratorListener l) {
    myDispatcher.removeListener(l);
  }

  final void dispose() {
    removeAll();

    Disposer.dispose(myHeader);
    myHeader = null;
    myProject = null;
  }

  /**
   * Fires event that "hide" button has been pressed.
   */
  final void fireHidden() {
    myDispatcher.getMulticaster().hidden(this);
  }

  /**
   * Fires event that "hide" button has been pressed.
   */
  final void fireHiddenSide() {
    myDispatcher.getMulticaster().hiddenSide(this);
  }

  /**
   * Fires event that user performed click into the title bar area.
   */
  final void fireActivated() {
    myDispatcher.getMulticaster().activated(this);
  }

  final void fireResized() {
    myDispatcher.getMulticaster().resized(this);
  }

  private void fireContentUiTypeChanges(@NotNull ToolWindowContentUiType type) {
    myDispatcher.getMulticaster().contentUiTypeChanges(this, type);
  }

  private void fireVisibleOnPanelChanged(final boolean visibleOnPanel) {
    myDispatcher.getMulticaster().visibleStripeButtonChanged(this, visibleOnPanel);
  }

  private void init(boolean dumbAware) {
    enableEvents(AWTEvent.COMPONENT_EVENT_MASK);

    final JPanel contentPane = new JPanel(new BorderLayout());
    installFocusTraversalPolicy(contentPane, new LayoutFocusTraversalPolicy());
    contentPane.add(myHeader, BorderLayout.NORTH);

    JPanel innerPanel = new JPanel(new BorderLayout());
    JComponent toolWindowComponent = myToolWindow.getComponent();
    if (!dumbAware) {
      toolWindowComponent = DumbService.getInstance(myProject).wrapGently(toolWindowComponent, myProject);
    }
    innerPanel.add(toolWindowComponent, BorderLayout.CENTER);

    final NonOpaquePanel inner = new NonOpaquePanel(innerPanel);

    contentPane.add(inner, BorderLayout.CENTER);
    add(contentPane, BorderLayout.CENTER);
    if (SystemInfo.isMac) {
      setBackground(new JBColor(Gray._200, Gray._90));
    }

    AncestorListener ancestorListener = new AncestorListener() {

      private static final String FOCUS_EDITOR_ACTION_KEY = "FOCUS_EDITOR_ACTION_KEY";

      @Override
      public void ancestorAdded(AncestorEvent event) {
        registerEscapeAction();
      }

      @Override
      public void ancestorMoved(AncestorEvent event) {}

      private void registerEscapeAction() {

        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(
          KeyEvent.VK_ESCAPE, 0),FOCUS_EDITOR_ACTION_KEY);
        getActionMap().put(FOCUS_EDITOR_ACTION_KEY, new AbstractAction() {
          @Override
          public void actionPerformed(ActionEvent e) {
            ToolWindowManager.getInstance(myProject).activateEditorComponent();
          }
        });
      }

      @Override
      public void ancestorRemoved(AncestorEvent event) {}
    };
    addAncestorListener(ancestorListener);
    Disposer.register(myProject, () -> removeAncestorListener(ancestorListener));
  }

  public void setTitleActions(@NotNull AnAction[] actions) {
    myHeader.setAdditionalTitleActions(actions);
  }

  void setTabActions(@NotNull AnAction[] actions) {
    myHeader.setTabActions(actions);
  }

  private class InnerPanelBorder implements Border {

    private final ToolWindow myWindow;

    private InnerPanelBorder(ToolWindow window) {
      myWindow = window;
    }

    @Override
    public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
      g.setColor(JBColor.border());
      doPaintBorder(c, g, x, y, width, height);
    }

    private void doPaintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      Insets insets = getBorderInsets(c);

      if (insets.top > 0) {
        UIUtil.drawLine(g, x, y + insets.top - 1, x + width - 1, y + insets.top - 1);
        UIUtil.drawLine(g, x, y + insets.top, x + width - 1, y + insets.top);
      }

      if (insets.left > 0) {
        UIUtil.drawLine(g, x, y, x, y + height);
        UIUtil.drawLine(g, x + 1, y, x + 1, y + height);
      }

      if (insets.right > 0) {
        UIUtil.drawLine(g, x + width - 1, y + insets.top, x + width - 1, y + height);
        UIUtil.drawLine(g, x + width, y + insets.top, x + width, y + height);
      }

      if (insets.bottom > 0) {
        UIUtil.drawLine(g, x, y + height - 1, x + width, y + height - 1);
        UIUtil.drawLine(g, x, y + height, x + width, y + height);
       }
    }

    @Override
    public Insets getBorderInsets(final Component c) {
      if (myProject == null) return new Insets(0, 0, 0, 0);
      ToolWindowManager toolWindowManager =  ToolWindowManager.getInstance(myProject);
      if (!(toolWindowManager instanceof ToolWindowManagerImpl)
          || !((ToolWindowManagerImpl)toolWindowManager).isToolWindowRegistered(myInfo.getId())
          || myWindow.getType() == ToolWindowType.FLOATING
          || myWindow.getType() == ToolWindowType.WINDOWED) {
        return new Insets(0, 0, 0, 0);
      }
      ToolWindowAnchor anchor = myWindow.getAnchor();
      Component component = myWindow.getComponent();
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

      int top =
        isSplitter && (anchor == ToolWindowAnchor.RIGHT || anchor == ToolWindowAnchor.LEFT) && myInfo.isSplit() && isVerticalSplitter
        ? -1
        : 0;
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
        Content content = myToolWindow.getContentManager().getSelectedContent();
        if (content != null) {
          String helpId = content.getHelpId();
          if (helpId != null) {
            return helpId;
          }
        }

        String id = myToolWindow.getHelpId();
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
    DefaultActionGroup resize = new DefaultActionGroup(ActionsBundle.groupText("ResizeToolWindowGroup"), true);
    resize.add(new ResizeToolWindowAction.Left(myToolWindow, this));
    resize.add(new ResizeToolWindowAction.Right(myToolWindow, this));
    resize.add(new ResizeToolWindowAction.Up(myToolWindow, this));
    resize.add(new ResizeToolWindowAction.Down(myToolWindow, this));
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
  final ToolWindowImpl getToolWindow() {
    return myToolWindow;
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
    fireVisibleOnPanelChanged(false);
    fireHidden();
  }

  void showStripeButton() {
    fireVisibleOnPanelChanged(true);
    fireActivated();
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
    @NonNls static final String HIDE_ACTIVE_WINDOW_ACTION_ID = InternalDecorator.HIDE_ACTIVE_WINDOW_ACTION_ID;

    HideAction() {
      copyFrom(ActionManager.getInstance().getAction(HIDE_ACTIVE_WINDOW_ACTION_ID));
      getTemplatePresentation().setText(UIBundle.message("tool.window.hide.action.name"));
    }

    @Override
    public final void actionPerformed(@NotNull final AnActionEvent e) {
      fireHidden();
    }

    @Override
    public final void update(@NotNull final AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      presentation.setEnabled(myInfo.isVisible());
    }
  }


  private final class ToggleContentUiTypeAction extends ToggleAction implements DumbAware {
    private boolean myHadSeveralContents;

    private ToggleContentUiTypeAction() {
      copyFrom(ActionManager.getInstance().getAction(TOGGLE_CONTENT_UI_TYPE_ACTION_ID));
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      myHadSeveralContents = myHadSeveralContents || myToolWindow.getContentManager().getContentCount() > 1;
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
    private Point myLastPoint;
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


    private class MyMouseAdapter extends MouseAdapter {

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
        if (!myDragging) return;
        MouseEvent event = SwingUtilities.convertMouseEvent(e.getComponent(), e, MyDivider.this);
        final ToolWindowAnchor anchor = myInfo.getAnchor();
        final Point point = event.getPoint();
        final Container windowPane = InternalDecorator.this.getParent();
        myLastPoint = SwingUtilities.convertPoint(MyDivider.this, point, windowPane);
        myLastPoint.x = Math.min(Math.max(myLastPoint.x, 0), windowPane.getWidth());
        myLastPoint.y = Math.min(Math.max(myLastPoint.y, 0), windowPane.getHeight());

        final Rectangle bounds = InternalDecorator.this.getBounds();
        if (anchor == ToolWindowAnchor.TOP) {
          InternalDecorator.this.setBounds(0, 0, bounds.width, myLastPoint.y);
        }
        else if (anchor == ToolWindowAnchor.LEFT) {
          InternalDecorator.this.setBounds(0, 0, myLastPoint.x, bounds.height);
        }
        else if (anchor == ToolWindowAnchor.BOTTOM) {
          InternalDecorator.this.setBounds(0, myLastPoint.y, bounds.width, windowPane.getHeight() - myLastPoint.y);
        }
        else if (anchor == ToolWindowAnchor.RIGHT) {
          InternalDecorator.this.setBounds(myLastPoint.x, 0, windowPane.getWidth() - myLastPoint.x, bounds.height);
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
    info.put("toolWindowTitle", myToolWindow.getTitle());

    final Content selection = myToolWindow.getContentManager().getSelectedContent();
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
        String title = StringUtil.defaultIfEmpty(myToolWindow.getTitle(), myToolWindow.getStripeTitle());
        title = StringUtil.defaultIfEmpty(title, myToolWindow.getId());
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
