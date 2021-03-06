// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.content;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.ui.popup.*;
import com.intellij.ui.content.TabbedContent;
import com.intellij.ui.popup.PopupState;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public final class TabbedContentTabLabel extends ContentTabLabel {
  private final PopupState<JBPopup> myPopupState = PopupState.forPopup();
  private final TabbedContent myContent;

  public TabbedContentTabLabel(@NotNull TabbedContent content, @NotNull TabContentLayout layout) {
    super(content, layout);
    myContent = content;
  }

  private boolean isPopupShown() {
    return myPopupState.isShowing();
  }

  @Override
  protected void selectContent() {
    IdeEventQueue.getInstance().getPopupManager().closeAllPopups();
    super.selectContent();

    if (hasMultipleTabs()) {
      if (myPopupState.isRecentlyHidden()) return; // do not show new popup
      final SelectContentTabStep step = new SelectContentTabStep(getContent());
      final ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
      myPopupState.prepareToShow(popup);
      popup.showUnderneathOf(this);
      popup.addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          repaint();
        }
      });
    }

  }

  @Override
  public void update() {
    super.update();
    if (myContent != null) {
      setText(myContent.getDisplayName());
    }
  }

  @Override
  protected void fillIcons(@NotNull List<? super AdditionalIcon> icons) {
    icons.add(new AdditionalIcon(new ActiveIcon(JBUI.CurrentTheme.ToolWindow.comboTabIcon(true),
                                                JBUI.CurrentTheme.ToolWindow.comboTabIcon(false))) {
      @NotNull
      @Override
      public Rectangle getRectangle() {
        return new Rectangle(getX(), 0, getIconWidth(), getHeight());
      }

      @Override
      public boolean getActive() {
        return mouseOverIcon(this) || isPopupShown();
      }

      @Override
      public boolean getAvailable() {
        return hasMultipleTabs();
      }

      @NotNull
      @Override
      public Runnable getAction() {
        return () -> selectContent();
      }
    });
    super.fillIcons(icons);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    myPopupState.hidePopup();
  }

  @NotNull
  @Override
  public TabbedContent getContent() {
    return myContent;
  }

  private boolean hasMultipleTabs() {
    return myContent != null && myContent.hasMultipleTabs();
  }
}
