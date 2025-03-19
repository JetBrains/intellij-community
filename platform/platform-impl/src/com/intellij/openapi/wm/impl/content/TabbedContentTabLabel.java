// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.content;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.wm.impl.content.tabActions.ContentTabAction;
import com.intellij.ui.content.TabbedContent;
import com.intellij.ui.popup.PopupState;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class TabbedContentTabLabel extends ContentTabLabel {
  private final PopupState<JBPopup> myPopupState = PopupState.forPopup();

  public TabbedContentTabLabel(@NotNull TabbedContent content, @NotNull TabContentLayout layout) {
    super(content, layout);
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
    //noinspection DialogTitleCapitalization
    setText(myContent.getDisplayName());
  }

  @Override
  protected void fillActions(@NotNull List<? super ContentTabAction> actions) {
    actions.add(new SelectContentTabAction());
    super.fillActions(actions);
  }

  @Override
  protected @NotNull AdditionalIcon createIcon(@NotNull ContentTabAction action) {
    return new TabbedContentTabAdditionalIcon(action);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    myPopupState.hidePopup();
  }

  @Override
  public @NotNull TabbedContent getContent() {
    return (TabbedContent)super.getContent();
  }

  private boolean hasMultipleTabs() {
    return getContent().hasMultipleTabs();
  }

  private final class SelectContentTabAction extends ContentTabAction {
    private SelectContentTabAction() {
      super(new ActiveIcon(JBUI.CurrentTheme.ToolWindow.comboTabIcon(true),
                           JBUI.CurrentTheme.ToolWindow.comboTabIcon(false)));
    }

    @Override
    public boolean getAvailable() {
      return hasMultipleTabs();
    }

    @Override
    public void runAction() {
      selectContent();
    }
  }

  protected final class TabbedContentTabAdditionalIcon extends ContentAdditionalIcon {
    public TabbedContentTabAdditionalIcon(@NotNull ContentTabAction action) {
      super(action);
    }

    @Override
    public boolean getActive() {
      return super.getActive() || isPopupShown();
    }
  }
}
