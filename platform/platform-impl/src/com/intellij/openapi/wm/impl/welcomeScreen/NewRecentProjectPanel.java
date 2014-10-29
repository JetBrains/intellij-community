/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.io.UniqueNameBuilder;
import com.intellij.openapi.wm.WelcomeScreen;
import com.intellij.ui.components.JBList;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
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
      scrollPane.setSize(245, 460);
      scrollPane.setMinimumSize(new Dimension(245, 460));
      scrollPane.setPreferredSize(new Dimension(245, 460));
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
    return list;
  }

  @Override
  protected ListCellRenderer createRenderer(UniqueNameBuilder<ReopenProjectAction> pathShortener) {
    return new RecentProjectItemRenderer(myPathShortener) {
      {
        setBorder(new EmptyBorder(0, 10, 0, 0));
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
        setLayout(new BorderLayout());
        myName.setBorder(new EmptyBorder(6, 0, 1, 5));
        myPath.setBorder(new EmptyBorder(1, 0, 6, 5));
        add(myName, BorderLayout.NORTH);
        add(myPath, BorderLayout.SOUTH);
      }
    };
  }
  
  

  @Nullable
  @Override
  protected JPanel createTitle() {
    return null;
  }

  
}
