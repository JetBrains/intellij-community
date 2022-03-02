// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status.widget;

import com.intellij.ide.HelpTooltipManager;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.TextPanel;
import com.intellij.ui.ClickListener;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.PopupState;
import com.intellij.util.Consumer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public interface StatusBarWidgetWrapper {
  @NotNull
  static JComponent wrap(@NotNull StatusBarWidget.WidgetPresentation presentation) {
    if (presentation instanceof StatusBarWidget.IconPresentation) {
      return new StatusBarWidgetWrapper.Icon((StatusBarWidget.IconPresentation)presentation);
    }
    else if (presentation instanceof StatusBarWidget.TextPresentation) {
      return new StatusBarWidgetWrapper.Text((StatusBarWidget.TextPresentation)presentation);
    }
    else if (presentation instanceof StatusBarWidget.MultipleTextValuesPresentation) {
      return new StatusBarWidgetWrapper.MultipleTextValues((StatusBarWidget.MultipleTextValuesPresentation)presentation);
    }
    else {
      throw new IllegalArgumentException("Unable to find a wrapper for presentation: " + presentation.getClass().getSimpleName());
    }
  }

  @NotNull
  StatusBarWidget.WidgetPresentation getPresentation();

  void beforeUpdate();

  default void setWidgetTooltip(JComponent widgetComponent, @NlsContexts.Tooltip @Nullable String toolTipText, @Nullable String shortcutText) {
    widgetComponent.setToolTipText(toolTipText);
    if (Registry.is("ide.helptooltip.enabled")) {
      widgetComponent.putClientProperty(HelpTooltipManager.SHORTCUT_PROPERTY, shortcutText);
    }
  }

  final class MultipleTextValues extends TextPanel.WithIconAndArrows implements StatusBarWidgetWrapper {
    private final StatusBarWidget.MultipleTextValuesPresentation myPresentation;

    public MultipleTextValues(@NotNull final StatusBarWidget.MultipleTextValuesPresentation presentation) {
      myPresentation = presentation;
      setVisible(StringUtil.isNotEmpty(myPresentation.getSelectedValue()));
      setTextAlignment(Component.CENTER_ALIGNMENT);
      setBorder(JBUI.CurrentTheme.StatusBar.Widget.border());
      new ClickListener() {
        private final PopupState<JBPopup> myPopupState = PopupState.forPopup();

        @Override
        public boolean onClick(@NotNull MouseEvent e, int clickCount) {
          if (myPopupState.isRecentlyHidden()) return false; // do not show new popup
          final ListPopup popup = myPresentation.getPopupStep();
          if (popup == null) return false;
          UIEventLogger.StatusBarPopupShown.log(myPresentation.getClass());
          final Dimension dimension = getSizeFor(popup);
          final Point at = new Point(0, -dimension.height);
          myPopupState.prepareToShow(popup);
          popup.show(new RelativePoint(e.getComponent(), at));
          return true;
        }

        private Dimension getSizeFor(ListPopup popup) {
          if (popup instanceof AbstractPopup) {
            return ((AbstractPopup)popup).getSizeForPositioning();
          }
          return popup.getContent().getPreferredSize();
        }
      }.installOn(this, true);
    }

    @Override
    public void beforeUpdate() {
      String value = myPresentation.getSelectedValue();
      setText(value);
      setIcon(myPresentation.getIcon());
      setVisible(StringUtil.isNotEmpty(value));
      setWidgetTooltip(this, myPresentation.getTooltipText(), myPresentation.getShortcutText());
    }

    @NotNull
    @Override
    public StatusBarWidget.WidgetPresentation getPresentation() {
      return myPresentation;
    }
  }

  final class Text extends TextPanel implements StatusBarWidgetWrapper {
    private final StatusBarWidget.TextPresentation myPresentation;

    public Text(@NotNull final StatusBarWidget.TextPresentation presentation) {
      myPresentation = presentation;
      setTextAlignment(presentation.getAlignment());
      setVisible(!myPresentation.getText().isEmpty());
      setBorder(JBUI.CurrentTheme.StatusBar.Widget.border());
      Consumer<MouseEvent> clickConsumer = myPresentation.getClickConsumer();
      if (clickConsumer != null) {
        new StatusBarWidgetClickListener(clickConsumer).installOn(this, true);
      }
    }

    @NotNull
    @Override
    public StatusBarWidget.WidgetPresentation getPresentation() {
      return myPresentation;
    }

    @Override
    public void beforeUpdate() {
      String text = myPresentation.getText();
      setText(text);
      setVisible(!text.isEmpty());
      setWidgetTooltip(this, myPresentation.getTooltipText(), myPresentation.getShortcutText());
    }
  }

  final class Icon extends TextPanel.WithIconAndArrows implements StatusBarWidgetWrapper {
    private final StatusBarWidget.IconPresentation myPresentation;

    public Icon(@NotNull final StatusBarWidget.IconPresentation presentation) {
      myPresentation = presentation;
      setTextAlignment(Component.CENTER_ALIGNMENT);
      setIcon(myPresentation.getIcon());
      setVisible(hasIcon());
      setBorder(JBUI.CurrentTheme.StatusBar.Widget.iconBorder());
      Consumer<MouseEvent> clickConsumer = myPresentation.getClickConsumer();
      if (clickConsumer != null) {
        new StatusBarWidgetClickListener(clickConsumer).installOn(this, true);
      }
    }

    @NotNull
    @Override
    public StatusBarWidget.WidgetPresentation getPresentation() {
      return myPresentation;
    }

    @Override
    public void beforeUpdate() {
      setIcon(myPresentation.getIcon());
      setVisible(hasIcon());
      setWidgetTooltip(this, myPresentation.getTooltipText(), myPresentation.getShortcutText());
    }
  }

  class StatusBarWidgetClickListener extends ClickListener {
    private final Consumer<? super MouseEvent> myClickConsumer;

    public StatusBarWidgetClickListener(@NotNull Consumer<? super MouseEvent> consumer) {
      myClickConsumer = consumer;
    }

    @Override
    public boolean onClick(@NotNull MouseEvent e, int clickCount) {
      if (!e.isPopupTrigger() && MouseEvent.BUTTON1 == e.getButton()) {
        UIEventLogger.StatusBarWidgetClicked.log(myClickConsumer.getClass());
        myClickConsumer.consume(e);
      }
      return true;
    }
  }
}
