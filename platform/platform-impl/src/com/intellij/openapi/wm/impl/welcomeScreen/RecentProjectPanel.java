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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.ClickListener;
import com.intellij.ui.ListUtil;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.Function;
import com.intellij.util.SystemProperties;
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
        int selectedIndex = myList.getSelectedIndex();
        if (selectedIndex >= 0) {
          if (myList.getCellBounds(selectedIndex, selectedIndex).contains(event.getPoint())) {
            Object selection = myList.getSelectedValue();

            if (selection != null) {
              ((AnAction)selection).actionPerformed(AnActionEvent.createFromInputEvent((AnAction)selection, event, ActionPlaces.WELCOME_SCREEN));
            }
          }
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


    ActionListener deleteAction = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ReopenProjectAction selection = (ReopenProjectAction)myList.getSelectedValue();

        final int rc = Messages.showOkCancelDialog(RecentProjectPanel.this,
                                                   "Remove '" + selection.getTemplatePresentation().getText() +
                                                   "' from recent projects list?",
                                                   "Remove Recent Project",
                                                   Messages.getQuestionIcon());
        if (rc == 0) {
          final RecentProjectsManagerBase manager = RecentProjectsManagerBase.getInstance();

          manager.removePath(selection.getProjectPath());
          ListUtil.removeSelectedItems(myList);
        }
      }
    };

    myList.registerKeyboardAction(deleteAction, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    myList.registerKeyboardAction(deleteAction, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    myList.addMouseMotionListener(new MouseMotionAdapter() {
      boolean myIsEngaged = false;
      public void mouseMoved(MouseEvent e) {
        if (myIsEngaged && !UIUtil.isSelectionButtonDown(e)) {
          Point point = e.getPoint();
          int index = myList.locationToIndex(point);
          myList.setSelectedIndex(index);

          if (myList.getCellBounds(index, index).contains(point)) {
            myList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          }
          else {
            myList.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
          }
        }
        else {
          myIsEngaged = true;
        }
      }
    });

    myList.setSelectedIndex(0);

    JBScrollPane scroll = new JBScrollPane(myList);
    scroll.setBorder(null);

    JComponent list = recentProjectActions.length == 0
                      ? myList
                      : ListWithFilter.wrap(myList, scroll, new Function<Object, String>() {
                        @Override
                        public String fun(Object o) {
                          ReopenProjectAction item = (ReopenProjectAction)o;
                          String home = SystemProperties.getUserHome();
                          String path = item.getProjectPath();
                          if (FileUtil.startsWith(path, home)) {
                            path = path.substring(home.length());
                          }
                          return item.getProjectName() + " " + path;
                        }
                      });
    add(list, BorderLayout.CENTER);

    JPanel title = new JPanel() {
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(super.getPreferredSize().width, 28);
      }
    };
    title.setBorder(new BottomLineBorder());

    JLabel titleLabel = new JLabel("Recent Projects");
    title.add(titleLabel);
    titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
    titleLabel.setForeground(WelcomeScreenColors.CAPTION_FOREGROUND);
    title.setBackground(WelcomeScreenColors.CAPTION_BACKGROUND);

    add(title, BorderLayout.NORTH);

    setBorder(new LineBorder(WelcomeScreenColors.BORDER_COLOR));
  }

  private static String getTitle2Text(String fullText, JComponent pathLabel) {
    int labelWidth = pathLabel.getWidth();
    if (fullText == null || fullText.length() == 0) return " ";

    String home = SystemProperties.getUserHome();
    if (FileUtil.startsWith(fullText, home)) {
      fullText = "~" + fullText.substring(home.length());
    }

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
    public Dimension getPreferredSize() {
      return new Dimension(250, 400);
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
