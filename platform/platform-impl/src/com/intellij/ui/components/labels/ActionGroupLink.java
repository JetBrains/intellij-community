// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.labels;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.ui.ClickListener;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MouseEventAdapter;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class ActionGroupLink extends JPanel {
  private final MyLinkLabel myLinkLabel;

  public ActionGroupLink(@NotNull String text, @Nullable Icon icon, @NotNull ActionGroup actionGroup) {
    setOpaque(false);

    myLinkLabel = new MyLinkLabel(text, icon);
    myLinkLabel.setListener(new LinkListener() {
      @Override
      public void linkSelected(LinkLabel aSource, Object aLinkData) {
        DataContext dataContext = DataManager.getInstance().getDataContext(myLinkLabel);
        ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(null, actionGroup, dataContext,
                                                                              JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false,
                                                                              ActionPlaces.UNKNOWN);
        ((PopupFactoryImpl.ActionGroupPopup)popup).showUnderneathOfLabel(myLinkLabel);
      }
    }, null);


    JLabel arrow = new JLabel(AllIcons.General.LinkDropTriangle);
    arrow.setVerticalAlignment(SwingConstants.BOTTOM);
    arrow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        final MouseEvent newEvent = MouseEventAdapter.convert(e, myLinkLabel, e.getX(), e.getY());
        myLinkLabel.doClick(newEvent);
        return true;
      }
    }.installOn(arrow);

    setLayout(new GridBagLayout());
    add(JBUI.Panels.simplePanel(myLinkLabel).addToRight(arrow), new GridBagConstraints());
  }

  public LinkLabel getLinkLabel() {
    return myLinkLabel;
  }

  private static class MyLinkLabel extends LinkLabel implements DataProvider {
    public MyLinkLabel(String text, @Nullable Icon icon) {
      super(text, icon);
    }

    @Override
    public Object getData(@NonNls String dataId) {
      if (PlatformDataKeys.DOMINANT_HINT_AREA_RECTANGLE.is(dataId)) {
        final Point p = SwingUtilities.getRoot(this).getLocationOnScreen();
        return new Rectangle(p.x, p.y + getHeight(), 0, 0);
      }
      if (PlatformDataKeys.CONTEXT_MENU_POINT.is(dataId)) {
        return SwingUtilities.convertPoint(this, 0, getHeight(), UIUtil.getRootPane(this));
      }

      return null;
    }
  }
}
