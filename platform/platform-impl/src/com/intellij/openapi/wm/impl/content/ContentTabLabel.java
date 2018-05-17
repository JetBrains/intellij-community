// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.content;

import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.popup.ActiveIcon;
import com.intellij.ui.EngravedTextGraphics;
import com.intellij.ui.Gray;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TimedDeadzone;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

class ContentTabLabel extends BaseLabel {
  private final int MAX_WIDTH = JBUI.scale(300);
  private final int DEFAULT_HORIZONTAL_INSET = JBUI.scale(12);
  protected static final int ICONS_GAP = JBUI.scale(3);

  private final ActiveIcon closeIcon = new ActiveIcon(JBUI.CurrentTheme.ToolWindow.closeTabIcon(true),
                                                      JBUI.CurrentTheme.ToolWindow.closeTabIcon(false));
  private final Content myContent;
  private final TabContentLayout myLayout;

  private final List<AdditionalIcon> additionalIcons = new ArrayList<>();
  private String txt = null;

  private final AdditionalIcon closeTabIcon = new AdditionalIcon(closeIcon) {
    private static final String ACTION_NAME = "Close tab";

    @NotNull
    @Override
    public Rectangle getRectangle() {
      return new Rectangle(getX(), 0, getIconWidth(), getHeight());
    }

    @Override
    public boolean getActive() {
      return mouseOverIcon(this);
    }

    @Override
    public boolean getAvailable() {
      return canBeClosed();
    }

    @NotNull
    @Override
    public Runnable getAction() {
      return () -> contentManager().removeContent(getContent(), true);
    }

    @Override
    public boolean getAfterText() {
      return UISettings.getShadowInstance().getCloseTabButtonOnTheRight() || !UISettings.getShadowInstance().getShowCloseButton();
    }

    @Nullable
    @Override
    public String getTooltip() {
      String text =
        KeymapUtil.getShortcutsText(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_CLOSE_ACTIVE_TAB));

      return text.isEmpty() || !isSelected() ? ACTION_NAME : ACTION_NAME + " (" + text + ")";
    }
  };

  private CurrentTooltip currentIconTooltip;

  private void showTooltip(AdditionalIcon icon) {

    if(icon != null) {
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
    if(txt != null && !txt.equals(getText())) {
      IdeTooltip tooltip = new IdeTooltip(this, getMousePosition(), new JLabel(txt));
      currentIconTooltip = new CurrentTooltip(IdeTooltipManager.getInstance().show(tooltip, false, false), null);
    }

  }

  private void hideCurrentTooltip() {
    if (currentIconTooltip == null) return;

    currentIconTooltip.currentTooltip.hide();
    currentIconTooltip = null;
  }

  BaseButtonBehavior behavior = new BaseButtonBehavior(this) {
    protected void execute(final MouseEvent e) {

      Optional<Runnable> first = additionalIcons.stream()
                                                .filter(icon -> mouseOverIcon(icon))
                                                .map(icon -> icon.getAction()).findFirst();

      if (first.isPresent()) {
        first.get().run();
        return;
      }

      selectContent();
    }
  };

  @Override
  public void setText(String text) {
    txt = text;
    int iconWidth = updateAndGetInsetsWidth();

    FontMetrics fm = getFontMetrics(getFont());
    int textWidth = SwingUtilities2.stringWidth(this, fm, text);
    int prefWidth = iconWidth + textWidth;

    int maxWidth = getMaximumSize().width;

    if(prefWidth > maxWidth) {
      int offset = maxWidth - iconWidth;
      String s = SwingUtilities2.clipString(this, fm, txt, offset);
      super.setText(s);
      return;
    }

    super.setText(txt);
  }

  protected final boolean mouseOverIcon(AdditionalIcon icon) {
    if (!isHovered() || !icon.getAvailable()) return false;

    PointerInfo info = MouseInfo.getPointerInfo();
    if (info == null) return false;
    Point point = info.getLocation();
    SwingUtilities.convertPointFromScreen(point, this);
    return icon.contains(point);
  }

  public ContentTabLabel(@NotNull Content content, @NotNull TabContentLayout layout) {
    super(layout.myUi, false);
    myLayout = layout;
    myContent = content;

    fillIcons(additionalIcons);

    behavior.setActionTrigger(MouseEvent.MOUSE_RELEASED);
    behavior.setMouseDeadzone(TimedDeadzone.NULL);

    myContent.addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent event) {
        final String property = event.getPropertyName();
        if (Content.IS_CLOSABLE.equals(property)) {
          repaint();
        }
      }
    });

    setMaximumSize(new Dimension(MAX_WIDTH, getMaximumSize().height));
  }

  protected void fillIcons(List<AdditionalIcon> icons) {
    icons.add(closeTabIcon);
  }

  @Override
  protected void processMouseMotionEvent(MouseEvent event) {
    super.processMouseMotionEvent(event);

    boolean hovered = isHovered();

    if (hovered) {
      if (invalid()) {
        repaint();
      }

      Optional<AdditionalIcon> first = additionalIcons.stream().filter(icon -> mouseOverIcon(icon)).findFirst();

      if (first.isPresent()) {
        showTooltip(first.get());
        return;
      }
    }

    showTooltip(null);
  }

  protected boolean invalid() {
    return additionalIcons.stream().anyMatch(icon -> icon.getAvailable());
  }

  public final boolean canBeClosed() {
    return myContent.isCloseable() && contentManager().canCloseContents();
  }

  protected void selectContent() {
    final ContentManager mgr = contentManager();
    if (mgr.getIndexOfContent(myContent) >= 0) {
      mgr.setSelectedContent(myContent, true);
    }
  }

  public void update() {
    setHorizontalAlignment(SwingConstants.LEFT);
    if (!myLayout.isToDrawTabs()) {
      setBorder(null);
    }

    updateTextAndIcon(myContent, isSelected());
  }

  @Override
  public Dimension getPreferredSize() {
    final Dimension size = super.getPreferredSize();
    int x = 0;
    Map<Boolean, List<AdditionalIcon>> map =
      additionalIcons.stream().filter(icon -> icon.getAvailable()).collect(Collectors.groupingBy(icon -> icon.getAfterText()));

    if (map.get(false) != null) {
      x = ICONS_GAP;

      for (AdditionalIcon icon : map.get(false)) {
        icon.setX(x);
        x += icon.getIconWidth() + ICONS_GAP;
      }

      x = 0;
    }

    x += size.width;

    if (map.get(true) != null) {
      for (AdditionalIcon icon : map.get(true)) {
        icon.setX(x + ICONS_GAP - getInsets().right);
        x += icon.getIconWidth() + ICONS_GAP;
      }
    }

    return new Dimension(x, size.height);
  }

  private int updateAndGetInsetsWidth() {
    if(additionalIcons == null) return 0;

    Map<Boolean, List<AdditionalIcon>> map =
      additionalIcons.stream().filter(icon -> icon.getAvailable()).collect(Collectors.groupingBy(icon -> icon.getAfterText()));

    int right = DEFAULT_HORIZONTAL_INSET;
    int left = DEFAULT_HORIZONTAL_INSET;

    int iconWidth = 0;

    if (map.get(false) != null) {
      iconWidth = ICONS_GAP;

      for (AdditionalIcon icon : map.get(false)) {
        iconWidth += icon.getIconWidth() + ICONS_GAP;
      }

      left = iconWidth;
      iconWidth = 0;
    }

    if (map.get(true) != null) {
      right = ICONS_GAP + JBUI.scale(4);

      for (AdditionalIcon icon : map.get(true)) {
        iconWidth += icon.getIconWidth() + ICONS_GAP;
      }
    }

    setBorder(JBUI.Borders.empty(0, left, 0, right));

    return iconWidth + right + left;
  }

  @Override
  protected boolean allowEngravement() {
    return isSelected() || (myUi != null && myUi.myWindow.isActive());
  }

  @Override
  protected Color getActiveFg(boolean selected) {
    if (contentManager().getContentCount() > 1) {
      return JBUI.CurrentTheme.Label.foreground(selected);
    }

    return super.getActiveFg(selected);
  }

  @Override
  protected Color getPassiveFg(boolean selected) {
    if (contentManager().getContentCount() > 1) {
      return JBUI.CurrentTheme.Label.foreground(selected);
    }

    return super.getPassiveFg(selected);
  }

  private void paintIcons(final Graphics g) {
    for (AdditionalIcon icon : additionalIcons) {
      if (icon.getAvailable()) {
        icon.paintIcon(this, g);
      }
    }
  }

  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);
    paintIcons(g);
  }

  public boolean isSelected() {
    return contentManager().isSelected(myContent);
  }

  public boolean isHovered() {
    return behavior.isHovered();
  }

  @Override
  protected Graphics _getGraphics(Graphics2D g) {
    if (isSelected() && contentManager().getContentCount() > 1) {
      return new EngravedTextGraphics(g, 1, 1, Gray._0.withAlpha(myUi.myWindow.isActive() ? 120 : 130));
    }

    return super._getGraphics(g);
  }

  private ContentManager contentManager() {
    return myUi.myWindow.getContentManager();
  }

  @NotNull
  @Override
  public Content getContent() {
    return myContent;
  }

  private static class CurrentTooltip {
    final IdeTooltip currentTooltip;
    final AdditionalIcon icon;

    public CurrentTooltip(IdeTooltip currentTooltip, AdditionalIcon icon) {
      this.currentTooltip = currentTooltip;
      this.icon = icon;
    }
  }
}
