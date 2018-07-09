// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.content;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.reference.SoftReference;
import com.intellij.ui.content.TabbedContent;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class TabbedContentTabLabel extends ContentTabLabel {

  private final TabbedContent myContent;
  private Reference<JBPopup> myPopupReference = null;

  public TabbedContentTabLabel(@NotNull TabbedContent content, @NotNull TabContentLayout layout) {
    super(content, layout);
    myContent = content;
  }

  private boolean isPopupShown() {
    return (myPopupReference != null && myPopupReference.get() != null && myPopupReference.get().isVisible());
  }

  @Override
  protected void selectContent() {
    IdeEventQueue.getInstance().getPopupManager().closeAllPopups();
    super.selectContent();

    if (hasMultipleTabs()) {
      final SelectContentTabStep step = new SelectContentTabStep(getContent());
      final ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
      myPopupReference = new WeakReference<>(popup);
      popup.showUnderneathOf(this);
      popup.addListener(new JBPopupAdapter() {
        @Override
        public void onClosed(LightweightWindowEvent event) {
          repaint();
        }
      });
    }

  }

  @Override
  public void update() {
    super.update();
    if (myContent != null) {
      setText(myContent.getTabName());
    }
  }

  @Override
  protected void fillIcons(List<AdditionalIcon> icons) {
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

      @Nullable
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
    JBPopup popup = SoftReference.dereference(myPopupReference);
    if (popup != null) {
      Disposer.dispose(popup);
      myPopupReference = null;
    }
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
