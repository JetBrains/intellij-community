/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.wm.impl.content;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Pair;
import com.intellij.reference.SoftReference;
import com.intellij.ui.ClickListener;
import com.intellij.ui.components.JBList;
import com.intellij.ui.content.TabbedContent;
import com.intellij.util.NotNullFunction;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.WatermarkIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * @author Konstantin Bulenkov
 */
public class TabbedContentTabLabel extends ContentTabLabel {
  private final ComboIcon myComboIcon = new ComboIcon() {
    @Override
    public Rectangle getIconRec() {
      return new Rectangle(getWidth() - getIconWidth() - 3, 0, getIconWidth(), getHeight());
    }

    @Override
    public boolean isActive() {
      return true;
    }
  };
  private final TabbedContent myContent;
  @Nullable private Reference<JBPopup> myPopupReference = null;

  public TabbedContentTabLabel(TabbedContent content, TabContentLayout layout) {
    super(content, layout);
    myContent = content;
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        showPopup();
        return true;
      }
    }.installOn(this);
  }

  private void showPopup() {
    IdeEventQueue.getInstance().getPopupManager().closeAllPopups();
    ArrayList<String> names = new ArrayList<>();
    for (Pair<String, JComponent> tab : myContent.getTabs()) {
      names.add(tab.first);
    }
    final JBList list = new JBList(names);
    list.installCellRenderer(new NotNullFunction<Object, JComponent>() {
      private final JLabel label = new JLabel();
      {
        label.setBorder(new EmptyBorder(UIUtil.getListCellPadding()));
      }
      @NotNull
      @Override
      public JComponent fun(Object dom) {
        String tabName = dom.toString();
        label.setText(tabName);
        setIconInPopupLabel(label, tabName);
        return label;
      }
    });
    final JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
      .setItemChoosenCallback(() -> {
        int index = list.getSelectedIndex();
        if (index != -1) {
          myContent.selectContent(index);
        }
      }).createPopup();
    myPopupReference = new WeakReference<>(popup);
    popup.showUnderneathOf(this);
  }

  private void setIconInPopupLabel(JLabel label, String tabName) {
    Icon baseIcon = getBaseIcon();
    boolean hasIconsInTabs = baseIcon != null;
    for (Pair<String, JComponent> nextTabWithName : myContent.getTabs()) {
      if (nextTabWithName.getFirst().equals(tabName)) {
        JComponent tab = nextTabWithName.getSecond();
        Icon tabIcon = null;
        if (tab instanceof Iconable) {
          tabIcon = ((Iconable)tab).getIcon(Iconable.ICON_FLAG_VISIBILITY);
          if (hasIconsInTabs && tabIcon == null) {
            tabIcon = EmptyIcon.create(baseIcon);
          }
        }
        label.setIcon(tabIcon);
      }
    }
  }

  @Nullable
  private Icon getBaseIcon() {
    Icon baseIcon = null;
    for (Pair<String, JComponent> nextTabWithName : myContent.getTabs()) {
      JComponent tabComponent = nextTabWithName.getSecond();
      if (tabComponent instanceof Iconable) {
        Icon tabIcon = ((Iconable)tabComponent).getIcon(Iconable.ICON_FLAG_VISIBILITY);
        if (tabIcon != null) {
          baseIcon = tabIcon;
          break;
        }
      }
    }
    return baseIcon;
  }

  @Override
  public void update() {
    super.update();
    if (myContent != null) {
      String tabName = myContent.getTabName();
      setText(tabName);
      setTabIcon(tabName, this);
    }
    setHorizontalAlignment(LEFT);
  }

  private void setTabIcon(String tabName, JLabel jLabel) {
    for (Pair<String, JComponent> nextTabWithName : myContent.getTabs()) {
      if (nextTabWithName.getFirst().equals(myContent.getTabNameWithoutPrefix(tabName))) {
        JComponent tab = nextTabWithName.getSecond();
        if (tab instanceof Iconable) {
          Icon baseIcon = ((Iconable)tab).getIcon(Iconable.ICON_FLAG_VISIBILITY);
          jLabel.setIcon(isSelected() || baseIcon == null ? baseIcon : new WatermarkIcon(baseIcon, .5f));
        }
      }
    }
  }

  @Override
  public Dimension getPreferredSize() {
    final Dimension size = super.getPreferredSize();
    return new Dimension(size.width + 12, size.height);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    myComboIcon.paintIcon(this, g);
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
}
