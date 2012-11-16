/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.ide.ReopenProjectAction;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.ClickListener;
import com.intellij.ui.Gray;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

public class RecentProjectPanel extends JPanel {
  private final JBList myList;

  public RecentProjectPanel() {
    super(new BorderLayout());

    final AnAction[] recentProjectActions = RecentProjectsManagerBase.getInstance().getRecentProjectsActions(false);
    myList = new MyList(recentProjectActions);
    myList.setCellRenderer(new RecentProjectItemRenderer());

    new ClickListener(){
      @Override
      public boolean onClick(MouseEvent event, int clickCount) {
        Object selection = myList.getSelectedValue();

        if (selection != null) {
          ((AnAction)selection).actionPerformed(AnActionEvent.createFromInputEvent((AnAction)selection, event, ActionPlaces.WELCOME_SCREEN));
        }

        return true;
      }
    }.installOn(myList);

    myList.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Object selection = myList.getSelectedValue();

        if (selection != null) {
          ((AnAction)selection).actionPerformed(AnActionEvent.createFromInputEvent((AnAction)selection, null, ActionPlaces.WELCOME_SCREEN));
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    myList.addMouseMotionListener(new MouseMotionAdapter() {
      boolean myIsEngaged = false;
      public void mouseMoved(MouseEvent e) {
        if (myIsEngaged && !UIUtil.isSelectionButtonDown(e)) {
          Point point = e.getPoint();
          int index = myList.locationToIndex(point);
          myList.setSelectedIndex(index);
        }
        else {
          myIsEngaged = true;
        }
      }
    });

    myList.setSelectedIndex(0);

    JBScrollPane scroll = new JBScrollPane(myList);
    scroll.setBorder(null);
    add(scroll, BorderLayout.CENTER);

    JPanel title = new JPanel() {
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(super.getPreferredSize().width, 28);
      }
    };

    JLabel titleLabel = new JLabel("Recent Projects");
    title.add(titleLabel);
    titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
    title.setBackground(Gray._210);

    add(title, BorderLayout.NORTH);

    setBorder(new LineBorder(Gray._190));
  }

  private static String getTitle2Text(String fullText, JComponent pathLabel) {
    int labelWidth = pathLabel.getWidth();
    if (fullText == null || fullText.length() == 0) return " ";
    while (pathLabel.getFontMetrics(pathLabel.getFont()).stringWidth(fullText) > labelWidth) {
      int sep = fullText.indexOf(File.separatorChar, 4);
      if (sep < 0) return fullText;
      fullText = "..." + fullText.substring(sep);
    }

    return fullText;
  }

  private static class MyList extends JBList {
    private MyList(@NotNull Object... listData) {
      super(listData);
      setEmptyText("No Project Open Yet");
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      Dimension size = super.getPreferredScrollableViewportSize();
      return new Dimension(250, size.height);
    }
  }

  private static class RecentProjectItemRenderer extends JPanel implements ListCellRenderer {
    private final JLabel myName = new JLabel();
    private final JLabel myPath = new JLabel();

    private RecentProjectItemRenderer() {
      super(new VerticalFlowLayout());
      setFocusable(true);
      myPath.setFont(myPath.getFont().deriveFont((float)10));
      add(myName);
      add(myPath);
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      ReopenProjectAction item = (ReopenProjectAction)value;

      Color fore = isSelected ? UIUtil.getListSelectionForeground() : list.getForeground();
      Color back = isSelected ? cellHasFocus ? UIUtil.getListSelectionBackground() : UIUtil.getListUnfocusedSelectionBackground() : list.getBackground();

      myName.setForeground(fore);
      myPath.setForeground(isSelected ? fore : UIUtil.getInactiveTextColor());

      setBackground(back);

      myName.setText(item.getProjectName());
      myPath.setText(getTitle2Text(item.getProjectPath(), myPath));

      return this;
    }
  }
}
