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
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.ReopenProjectAction;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.io.UniqueNameBuilder;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.WelcomeScreen;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.components.JBList;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * @author Konstantin Bulenkov
 */
public class NewRecentProjectPanel extends RecentProjectPanel {
  public NewRecentProjectPanel(WelcomeScreen screen) {
    super(screen);
    setBorder(null);
    setBackground(FlatWelcomeFrame.getProjectsBackground());
    JScrollPane scrollPane = UIUtil.findComponentOfType(this, JScrollPane.class);
    if (scrollPane != null) {
      scrollPane.setBackground(FlatWelcomeFrame.getProjectsBackground());
      scrollPane.setSize(JBUI.size(245, 460));
      scrollPane.setMinimumSize(JBUI.size(245, 460));
      scrollPane.setPreferredSize(JBUI.size(245, 460));
    }
    ListWithFilter panel = UIUtil.findComponentOfType(this, ListWithFilter.class);
    if (panel != null) {
      panel.setBackground(FlatWelcomeFrame.getProjectsBackground());
    }
  }

  protected Dimension getPreferredScrollableViewportSize() {
    return null;//new Dimension(250, 430);
  }
  
  @Override
  protected JBList createList(AnAction[] recentProjectActions, Dimension size) {
    final JBList list = super.createList(recentProjectActions, size);
    list.setBackground(FlatWelcomeFrame.getProjectsBackground());
    list.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
          FlatWelcomeFrame frame = UIUtil.getParentOfType(FlatWelcomeFrame.class, list);
          if (frame != null) {
            FocusTraversalPolicy policy = frame.getFocusTraversalPolicy();
            if (policy != null) {
              Component next = policy.getComponentAfter(frame, list);
              if (next != null) {
                next.requestFocus();
              }
            }
          }
        }
      }
    });
    list.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        final ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("WelcomeScreenRecentProjectActionGroup");
        if (group != null) {
          ActionManager.getInstance().createActionPopupMenu(ActionPlaces.WELCOME_SCREEN, group).getComponent().show(comp, x, y);
        }
      }
    });
    return list;
  }

  @Override
  protected boolean isUseGroups() {
    return FlatWelcomeFrame.isUseProjectGroups();
  }

  @Override
  protected ListCellRenderer createRenderer(UniqueNameBuilder<ReopenProjectAction> pathShortener) {
    return new RecentProjectItemRenderer(myPathShortener) {
       private GridBagConstraints nameCell;
       private GridBagConstraints pathCell;
       private GridBagConstraints closeButtonCell;

      private void initConstraints () {
        nameCell = new GridBagConstraints();
        pathCell = new GridBagConstraints();
        closeButtonCell = new GridBagConstraints();

        nameCell.gridx = 0;
        nameCell.gridy = 0;
        nameCell.weightx = 1.0;
        nameCell.weighty = 1.0;
        nameCell.anchor = GridBagConstraints.FIRST_LINE_START;
        nameCell.insets = JBUI.insets(6, 5, 1, 5);



        pathCell.gridx = 0;
        pathCell.gridy = 1;

        pathCell.insets = JBUI.insets(1, 5, 6, 5);
        pathCell.anchor = GridBagConstraints.LAST_LINE_START;


        closeButtonCell.gridx = 1;
        closeButtonCell.gridy = 0;
        closeButtonCell.anchor = GridBagConstraints.FIRST_LINE_END;
        closeButtonCell.insets = JBUI.insets(7, 7, 7, 7);
        closeButtonCell.gridheight = 2;

        //closeButtonCell.anchor = GridBagConstraints.WEST;
      }

      @Override
      protected Color getListBackground(boolean isSelected, boolean hasFocus) {
        return isSelected ? FlatWelcomeFrame.getListSelectionColor(hasFocus) : FlatWelcomeFrame.getProjectsBackground();
      }

      @Override
      protected Color getListForeground(boolean isSelected, boolean hasFocus) {
        return UIUtil.getListForeground(isSelected && hasFocus);
      }

      @Override
      protected void layoutComponents() {
        setLayout(new GridBagLayout());
        initConstraints();
        add(myName, nameCell);
        add(myPath, pathCell);
      }

      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {

        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        if (Registry.is("removable.welcome.screen.projects")) {
          if (myHovered) {
            add(myCloseThisItem, closeButtonCell);
            list.revalidate();
          }
          else {
            remove(myCloseThisItem);
            list.revalidate();
          }
        }

        return this;
      }

      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        int h = myName.getPreferredSize().height + myPath.getPreferredSize().height;
        return new Dimension(size.width, h + JBUI.scale(26));
      }
    };
  }
  
  

  @Nullable
  @Override
  protected JPanel createTitle() {
    return null;
  }

  
}
