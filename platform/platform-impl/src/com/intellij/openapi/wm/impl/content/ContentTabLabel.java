// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.content;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.popup.ActiveIcon;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.impl.content.tabActions.ContentTabAction;
import com.intellij.ui.EngravedTextGraphics;
import com.intellij.ui.Gray;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.concurrency.EdtScheduler;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtilities;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;

public class ContentTabLabel extends ContentLabel {
  private static final int MAX_WIDTH = JBUIScale.scale(400);

  private final LayeredIcon myActiveCloseIcon = LayeredIcon.layeredIcon(() -> new Icon[]{JBUI.CurrentTheme.ToolWindow.closeTabIcon(true)});
  private final LayeredIcon myRegularCloseIcon = LayeredIcon.layeredIcon(() -> new Icon[]{JBUI.CurrentTheme.ToolWindow.closeTabIcon(false)});
  protected final @NotNull Content myContent;
  private final TabContentLayout myLayout;

  private @NlsContexts.Label String myText;

  @Override
  protected void handleMouseClick(@NotNull MouseEvent e) {
    if (e.getID() == MouseEvent.MOUSE_RELEASED) {
      if (e.isAltDown()) {
        closeAllOtherTabs();
        return;
      }
      if (handleActionsClick(e)) return;
      selectContent();
      handleDoubleClick(e);
    }
  }

  private void handleDoubleClick(@NotNull MouseEvent e) {
    if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1 && !myLayout.doubleClickActions.isEmpty()) {
      DataContext dataContext = DataManager.getInstance().getDataContext(this);
      for (AnAction action : myLayout.doubleClickActions) {
        AnActionEvent event = AnActionEvent.createFromInputEvent(e, ActionPlaces.UNKNOWN, null, dataContext);
        ActionUtil.performAction(action, event);
      }
    }
  }

  @Override
  boolean showLabelText(@NotNull Content content) {
    return !Boolean.FALSE.equals(content.getUserData(ToolWindow.SHOW_CONTENT_TAB_LABEL_TEXT));
  }

  @Override
  public void setText(@NlsContexts.Label String text) {
    myText = text;
    updateText();
  }

  private boolean textUpdateScheduled;
  private void updateText() {
    try {
      if (myText != null && myText.startsWith("<html>")) {
        super.setText(myText); // SwingUtilities2.clipString does not support HTML
        return;
      }
      FontMetrics fm = getFontMetrics(getFont());
      int textWidth = UIUtilities.stringWidth(this, fm, myText);
      int prefWidth = myIconWithInsetsWidth + textWidth;

      int maxWidth = getMaximumSize().width;

      if (prefWidth > maxWidth) {
        int offset = maxWidth - myIconWithInsetsWidth;
        String s = UIUtilities.clipString(this, fm, myText, offset);
        super.setText(s);
        return;
      }

      super.setText(myText);
    }
    finally {
      //noinspection ConstantConditions
      if (myContent != null && !(myContent instanceof SingleContentLayout.SubContent) && !Disposer.isDisposed(myContent) && !textUpdateScheduled) {
        textUpdateScheduled = true;
        EdtScheduler.getInstance().schedule(50, () -> {
          textUpdateScheduled = false;
          Container parent = getParent();
          if (parent != null) {
            parent.revalidate();
            parent.repaint();
          }
        });
      }
    }
  }

  ContentTabLabel(@NotNull Content content, @NotNull TabContentLayout layout) {
    super(layout.ui, false);
    myLayout = layout;
    myContent = content;

    updateAdditionalActions();

    myContent.addPropertyChangeListener(event -> {
      final String property = event.getPropertyName();
      if (Content.IS_CLOSABLE.equals(property)) {
        repaint();
      }
      if (Content.PROP_PINNED.equals(property)) {
        updateCloseIcon();
      }
    });
    if (myContent.isPinned()) {
      SwingUtilities.invokeLater(this::updateCloseIcon);
    }
    setMaximumSize(new Dimension(MAX_WIDTH, getMaximumSize().height));
  }

  @Override
  protected String getOriginalText() {
    return myText;
  }

  private void updateCloseIcon() {
    boolean pinned = getContent().isPinned();
    myActiveCloseIcon.setIcon(pinned ? AllIcons.Actions.PinTab : JBUI.CurrentTheme.ToolWindow.closeTabIcon(true), 0);
    myRegularCloseIcon.setIcon(pinned ? AllIcons.Actions.PinTab : JBUI.CurrentTheme.ToolWindow.closeTabIcon(false), 0);
    repaint();
  }

  @Override
  protected void fillActions(@NotNull List<? super ContentTabAction> actions) {
    super.fillActions(actions);
    actions.add(new CloseContentTabAction());
  }

  public final boolean canBeClosed() {
    return myContent.isCloseable() && myUi.window.canCloseContents();
  }

  protected void selectContent() {
    ContentManager manager = getContentManager();
    if (manager.getIndexOfContent(myContent) >= 0) {
      manager.setSelectedContent(myContent, true);
    }
  }

  protected void closeContent() {
    getContentManager().removeContent(myContent, true);
  }

  public void update() {
    setHorizontalAlignment(SwingConstants.LEFT);
    if (myLayout.isToDrawTabs() == TabContentLayout.TabsDrawMode.HIDE) {
      myBorder.setBorderInsets(0, 0, 0, 0);
    }

    updateTextAndIcon(myContent, isSelected(), false);
  }

  @Override
  protected boolean allowEngravement() {
    return isSelected() || myUi != null && myUi.window.isActive();
  }

  @Override
  protected Color getActiveFg(boolean selected) {
    ContentManager contentManager = getContentManager();
    if (contentManager.getContentCount() > 1) {
      return selected ? JBUI.CurrentTheme.ToolWindow.underlinedTabForeground() : JBUI.CurrentTheme.Label.foreground(false);
    }

    return super.getActiveFg(selected);
  }

  @Override
  protected Color getPassiveFg(boolean selected) {
    ContentManager contentManager = myUi.window.getContentManagerIfCreated();
    if (contentManager != null && contentManager.getContentCount() > 1) {
      return selected ? JBUI.CurrentTheme.ToolWindow.underlinedTabInactiveForeground() : JBUI.CurrentTheme.Label.foreground(false);
    }

    return super.getPassiveFg(selected);
  }

  public boolean isSelected() {
    return getContentManager().isSelected(myContent);
  }

  @Override
  protected Graphics _getGraphics(Graphics2D g) {
    if (isSelected() && getContentManager().getContentCount() > 1) {
      return new EngravedTextGraphics(g, 1, 1, Gray._0.withAlpha(myUi.window.isActive() ? 120 : 130));
    }
    return super._getGraphics(g);
  }

  private @NotNull ContentManager getContentManager() {
    return myUi.getContentManager();
  }

  @Override
  public @NotNull Content getContent() {
    return myContent;
  }

  private final class CloseContentTabAction extends ContentTabAction {
    private CloseContentTabAction() {
      super(new ActiveIcon(myActiveCloseIcon, myRegularCloseIcon));
    }

    @Override
    public boolean getAvailable() {
      return canBeClosed();
    }

    @Override
    public void runAction() {
      Content content = getContent();
      if (content.isPinned()) {
        content.setPinned(false);
        return;
      }
      if (Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() == InputEvent.ALT_DOWN_MASK) {
        closeAllOtherTabs();
      } else {
        closeContent();
      }
    }

    @Override
    public boolean getAfterText() {
      return UISettings.getShadowInstance().getCloseTabButtonOnTheRight() || !UISettings.getShadowInstance().getShowCloseButton();
    }

    @Override
    public @NotNull String getTooltip() {
      if (getContent().isPinned()) {
        return IdeBundle.message("action.unpin.tab.tooltip");
      }
      Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_CLOSE_ACTIVE_TAB);
      String text = KeymapUtil.getShortcutsText(shortcuts);
      return text.isEmpty() || !isSelected()
             ? IdeBundle.message("tooltip.close.tab")
             : IdeBundle.message("tooltip.close.tab") + " (" + text + ")";
    }
  }

  private void closeAllOtherTabs() {
    ContentManager contentManager = getContentManager();
    for (Content content : contentManager.getContents()) {
      if (content != myContent && content.isCloseable()) {
        contentManager.removeContent(content, true);
      }
    }
  }
}
