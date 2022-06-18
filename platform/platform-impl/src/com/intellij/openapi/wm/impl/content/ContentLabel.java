// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.content;

import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.content.tabActions.ContentTabAction;
import com.intellij.openapi.wm.impl.content.tabActions.ContentTabActionProvider;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.content.Content;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TimedDeadzone;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class ContentLabel extends BaseLabel {
  private static final int DEFAULT_HORIZONTAL_INSET = JBUIScale.scale(12);
  protected static final int ICONS_GAP = JBUIScale.scale(3);

  private final List<AdditionalIcon> myAdditionalIcons = new SmartList<>();
  protected int myIconWithInsetsWidth;

  private CurrentTooltip currentIconTooltip;

  private final BaseButtonBehavior behavior = new BaseButtonBehavior(this) {
    @Override
    protected void execute(@NotNull MouseEvent e) {
      handleMouseClick(e);
    }
  };

  public ContentLabel(@NotNull ToolWindowContentUi ui, boolean bold) {
    super(ui, bold);

    behavior.setActionTrigger(BaseButtonBehavior.MOUSE_PRESSED_RELEASED);
    behavior.setMouseDeadzone(TimedDeadzone.NULL);
  }

  protected abstract void handleMouseClick(@NotNull MouseEvent e);

  @Nullable
  @NlsContexts.Label
  protected abstract String getOriginalText();

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

    String originalText = getOriginalText();
    if (originalText != null && !originalText.equals(getText())) {
      IdeTooltip tooltip = new IdeTooltip(this, getMousePosition(), new JLabel(originalText));
      currentIconTooltip = new CurrentTooltip(IdeTooltipManager.getInstance().show(tooltip, false, false), null);
    }
  }

  private void hideCurrentTooltip() {
    if (currentIconTooltip == null) return;

    currentIconTooltip.currentTooltip.hide();
    currentIconTooltip = null;
  }

  protected boolean handleActionsClick(@NotNull MouseEvent e) {
    for (AdditionalIcon icon : myAdditionalIcons) {
      if (mouseOverIcon(icon)) {
        icon.runAction();
        e.consume();
        return true;
      }
    }
    return false;
  }

  final boolean mouseOverIcon(AdditionalIcon icon) {
    if (!isHovered() || !icon.getAvailable()) return false;

    PointerInfo info = MouseInfo.getPointerInfo();
    if (info == null) return false;
    Point point = info.getLocation();
    SwingUtilities.convertPointFromScreen(point, this);
    return icon.contains(point);
  }

  protected void updateAdditionalActions() {
    myAdditionalIcons.clear();
    List<ContentTabAction> objects = new ArrayList<>();
    fillActions(objects);
    myAdditionalIcons.addAll(ContainerUtil.map(objects, this::createIcon));
  }

  protected void fillActions(@NotNull List<? super ContentTabAction> actions) {
    Content content = getContent();
    if (content != null) {
      ContentTabActionProvider.EP_NAME.forEachExtensionSafe(provider -> {
        actions.addAll(provider.createTabActions(content));
      });
    }
  }

  protected @NotNull AdditionalIcon createIcon(@NotNull ContentTabAction action) {
    return new ContentAdditionalIcon(action);
  }

  @Override
  protected void processMouseMotionEvent(MouseEvent event) {
    super.processMouseMotionEvent(event);

    boolean hovered = isHovered();

    if (hovered) {
      if (hasActiveIcons()) {
        repaint();
      }

      AdditionalIcon first = findHoveredIcon();
      if (first != null) {
        showTooltip(first);
        return;
      }
    }

    showTooltip(null);
  }

  @Nullable
  protected AdditionalIcon findHoveredIcon() {
    return ContainerUtil.find(myAdditionalIcons, icon -> mouseOverIcon(icon));
  }

  boolean hasActiveIcons() {
    return myAdditionalIcons.stream().anyMatch(icon -> icon.getAvailable());
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

    boolean additionalIconsOnly = StringUtil.isEmptyOrSpaces(getText()) && getIcon() == null;
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

    if (ExperimentalUI.isNewUI()) {
      setBorder(JBUI.Borders.empty(JBUI.CurrentTheme.ToolWindow.headerTabLeftRightInsets()));
      myIconWithInsetsWidth = rightIconWidth;
    }

    return new Dimension(rightIconWidth + size.width, size.height);
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

  public boolean isHovered() {
    return behavior.isHovered();
  }

  private static final class CurrentTooltip {
    final IdeTooltip currentTooltip;
    final AdditionalIcon icon;

    CurrentTooltip(IdeTooltip currentTooltip, AdditionalIcon icon) {
      this.currentTooltip = currentTooltip;
      this.icon = icon;
    }
  }

  protected class ContentAdditionalIcon extends AdditionalIcon {
    public ContentAdditionalIcon(@NotNull ContentTabAction action) {
      super(action);
    }

    @Override
    public boolean getActive() {
      return mouseOverIcon(this);
    }

    @Override
    public int getHeight() {
      return ContentLabel.this.getHeight();
    }
  }
}
