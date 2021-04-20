// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.content;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.popup.ActiveIcon;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.content.tabActions.ContentTabAction;
import com.intellij.openapi.wm.impl.content.tabActions.ContentTabActionProvider;
import com.intellij.ui.EngravedTextGraphics;
import com.intellij.ui.Gray;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TimedDeadzone;
import com.intellij.util.ui.UIUtilities;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.*;

class ContentTabLabel extends BaseLabel {
  private static final int MAX_WIDTH = JBUIScale.scale(400);
  private static final int DEFAULT_HORIZONTAL_INSET = JBUIScale.scale(12);
  private static final int ICONS_GAP = JBUIScale.scale(3);
  private final LayeredIcon myActiveCloseIcon = new LayeredIcon(JBUI.CurrentTheme.ToolWindow.closeTabIcon(true));
  private final LayeredIcon myRegularCloseIcon = new LayeredIcon(JBUI.CurrentTheme.ToolWindow.closeTabIcon(false));
  @NotNull
  protected final Content myContent;
  private final TabContentLayout myLayout;

  private final List<AdditionalIcon> myAdditionalIcons = new SmartList<>();
  private @NlsContexts.Label String myText;
  private int myIconWithInsetsWidth;

  private CurrentTooltip currentIconTooltip;

  private void showTooltip(AdditionalIcon icon) {
    if (icon != null) {
      if (currentIconTooltip != null) {
        if (currentIconTooltip.icon == icon) {
          IdeTooltipManager.getInstance().show(currentIconTooltip.currentTooltip, false, false);
          return;
        }

        hideCurrentTooltip();
      }

      String toolText = icon.getTooltip();
      if (toolText != null && !toolText.isEmpty()) {
        IdeTooltip tooltip = new IdeTooltip(this, icon.getCenterPoint(), new JLabel(toolText));
        currentIconTooltip = new CurrentTooltip(IdeTooltipManager.getInstance().show(tooltip, false, false), icon);
        return;
      }
    }

    hideCurrentTooltip();
    if(myText != null && !myText.equals(getText())) {
      IdeTooltip tooltip = new IdeTooltip(this, getMousePosition(), new JLabel(myText));
      currentIconTooltip = new CurrentTooltip(IdeTooltipManager.getInstance().show(tooltip, false, false), null);
    }
  }

  private void hideCurrentTooltip() {
    if (currentIconTooltip == null) return;

    currentIconTooltip.currentTooltip.hide();
    currentIconTooltip = null;
  }

  private final BaseButtonBehavior behavior = new BaseButtonBehavior(this) {
    @Override
    protected void execute(@NotNull MouseEvent e) {
      for (AdditionalIcon icon : myAdditionalIcons) {
        if (mouseOverIcon(icon)) {
          icon.runAction();
          return;
        }
      }

      selectContent();

      if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1 && !myLayout.myDoubleClickActions.isEmpty()) {
        DataContext dataContext = DataManager.getInstance().getDataContext(ContentTabLabel.this);
        for (AnAction action : myLayout.myDoubleClickActions) {
          AnActionEvent event = AnActionEvent.createFromInputEvent(e, ActionPlaces.UNKNOWN, null, dataContext);
          if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
            ActionUtil.performActionDumbAwareWithCallbacks(action, event);
          }
        }
      }
    }
  };

  @Override
  public void setText(@NlsContexts.Label String text) {
    myText = text;
    updateText();
  }

  private void updateText() {
    if (myText != null && myText.startsWith("<html>")) {
      super.setText(myText); // SwingUtilities2.clipString does not support HTML
      return;
    }
    FontMetrics fm = getFontMetrics(getFont());
    int textWidth = UIUtilities.stringWidth(this, fm, myText);
    int prefWidth = myIconWithInsetsWidth + textWidth;

    int maxWidth = getMaximumSize().width;

    if(prefWidth > maxWidth) {
      int offset = maxWidth - myIconWithInsetsWidth;
      String s = UIUtilities.clipString(this, fm, myText, offset);
      super.setText(s);
      return;
    }

    super.setText(myText);
  }

  final boolean mouseOverIcon(AdditionalIcon icon) {
    if (!isHovered() || !icon.getAvailable()) return false;

    PointerInfo info = MouseInfo.getPointerInfo();
    if (info == null) return false;
    Point point = info.getLocation();
    SwingUtilities.convertPointFromScreen(point, this);
    return icon.contains(point);
  }

  ContentTabLabel(@NotNull Content content, @NotNull TabContentLayout layout) {
    super(layout.myUi, false);
    myLayout = layout;
    myContent = content;

    List<ContentTabAction> objects = new ArrayList<>();
    fillActions(objects);
    myAdditionalIcons.addAll(ContainerUtil.map(objects, this::createIcon));

    behavior.setActionTrigger(MouseEvent.MOUSE_RELEASED);
    behavior.setMouseDeadzone(TimedDeadzone.NULL);

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

  private void updateCloseIcon() {
    boolean pinned = getContent().isPinned();
    myActiveCloseIcon.setIcon(pinned ? AllIcons.Actions.PinTab : JBUI.CurrentTheme.ToolWindow.closeTabIcon(true), 0);
    myRegularCloseIcon.setIcon(pinned ? AllIcons.Actions.PinTab : JBUI.CurrentTheme.ToolWindow.closeTabIcon(false), 0);
    repaint();
  }

  protected void fillActions(@NotNull List<? super ContentTabAction> actions) {
    ContentTabActionProvider.EP_NAME.forEachExtensionSafe(provider -> {
      actions.addAll(provider.createTabActions(myContent));
    });

    actions.add(new CloseContentTabAction());
  }

  protected @NotNull AdditionalIcon createIcon(@NotNull ContentTabAction action) {
    return new ContentTabAdditionalIcon(action);
  }

  @Override
  protected void processMouseMotionEvent(MouseEvent event) {
    super.processMouseMotionEvent(event);

    boolean hovered = isHovered();

    if (hovered) {
      if (hasActiveIcons()) {
        repaint();
      }

      AdditionalIcon first = ContainerUtil.find(myAdditionalIcons, icon -> mouseOverIcon(icon));

      if (first != null) {
        showTooltip(first);
        return;
      }
    }

    showTooltip(null);
  }

  boolean hasActiveIcons() {
    return myAdditionalIcons.stream().anyMatch(icon -> icon.getAvailable());
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

  public void update() {
    setHorizontalAlignment(SwingConstants.LEFT);
    if (myLayout.isToDrawTabs() == TabContentLayout.TabsDrawMode.HIDE) {
      setBorder(null);
    }

    updateTextAndIcon(myContent, isSelected());
  }

  @Override
  public Dimension getPreferredSize() {
    final Dimension size = super.getPreferredSize();
    Map<Boolean, List<AdditionalIcon>> map = new HashMap<>();
    for (AdditionalIcon myAdditionalIcon : myAdditionalIcons) {
      if (myAdditionalIcon.getAvailable()) {
        map.computeIfAbsent(myAdditionalIcon.getAfterText(), k -> new SmartList<>()).add(myAdditionalIcon);
      }
    }

    boolean additionalIconsOnly = StringUtil.isEmptyOrSpaces(getText());
    int left = DEFAULT_HORIZONTAL_INSET;
    int right = DEFAULT_HORIZONTAL_INSET;
    if (additionalIconsOnly) {
      left = ICONS_GAP;
      right = ICONS_GAP;
    }

    if (map.get(false) != null) {
      int iconWidth = ICONS_GAP;

      for (AdditionalIcon icon : map.get(false)) {
        icon.setX(iconWidth);
        iconWidth += icon.getIconWidth() + ICONS_GAP;
      }

      left = iconWidth;
    }

    int rightIconWidth = 0;
    if (map.get(true) != null) {
      if (additionalIconsOnly) {
        for (AdditionalIcon icon : map.get(true)) {
          icon.setX(left + rightIconWidth);
          rightIconWidth += icon.getIconWidth() + ICONS_GAP;
        }
        rightIconWidth -= ICONS_GAP;
      }
      else {
        right = ICONS_GAP + JBUIScale.scale(4);
        int offset = size.width - JBUIScale.scale(4);

        for (AdditionalIcon icon : map.get(true)) {
          icon.setX(offset + rightIconWidth);
          rightIconWidth += icon.getIconWidth() + ICONS_GAP;
        }
      }
    }

    setBorder(new EmptyBorder(0, left, 0, right));
    myIconWithInsetsWidth = rightIconWidth + right + left;

    return new Dimension(rightIconWidth + size.width, size.height);
  }

  @Override
  protected boolean allowEngravement() {
    return isSelected() || myUi != null && myUi.window.isActive();
  }

  @Override
  protected Color getActiveFg(boolean selected) {
    ContentManager contentManager = myUi.window.getContentManagerIfCreated();
    if (contentManager != null && contentManager.getContentCount() > 1) {
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

  private void paintIcons(@NotNull Graphics g) {
    for (AdditionalIcon icon : myAdditionalIcons) {
      if (icon.getAvailable()) {
        icon.paintIcon(this, g);
      }
    }
  }

  @Override
  protected void paintComponent(@NotNull Graphics g) {
    super.paintComponent(g);
    paintIcons(g);
  }

  public boolean isSelected() {
    ContentManager contentManager = myUi.window.getContentManagerIfCreated();
    return contentManager != null && contentManager.isSelected(myContent);
  }

  public boolean isHovered() {
    return behavior.isHovered();
  }

  @Override
  protected Graphics _getGraphics(Graphics2D g) {
    if (isSelected() && getContentManager().getContentCount() > 1) {
      return new EngravedTextGraphics(g, 1, 1, Gray._0.withAlpha(myUi.window.isActive() ? 120 : 130));
    }
    return super._getGraphics(g);
  }

  @NotNull
  private ContentManager getContentManager() {
    return myUi.getContentManager();
  }

  @NotNull
  @Override
  public Content getContent() {
    return myContent;
  }

  private static final class CurrentTooltip {
    final IdeTooltip currentTooltip;
    final AdditionalIcon icon;

    CurrentTooltip(IdeTooltip currentTooltip, AdditionalIcon icon) {
      this.currentTooltip = currentTooltip;
      this.icon = icon;
    }
  }

  private class CloseContentTabAction extends ContentTabAction {
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
      ContentManager contentManager = myUi.window.getContentManagerIfCreated();
      if (contentManager != null) {
        contentManager.removeContent(content, true);
      }
    }

    @Override
    public boolean getAfterText() {
      return UISettings.getShadowInstance().getCloseTabButtonOnTheRight() || !UISettings.getShadowInstance().getShowCloseButton();
    }

    @NotNull
    @Override
    public String getTooltip() {
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

  protected class ContentTabAdditionalIcon extends AdditionalIcon {
    public ContentTabAdditionalIcon(@NotNull ContentTabAction action) {
      super(action);
    }

    @Override
    public boolean getActive() {
      return mouseOverIcon(this);
    }

    @Override
    public int getHeight() {
      return ContentTabLabel.this.getHeight();
    }
  }
}
