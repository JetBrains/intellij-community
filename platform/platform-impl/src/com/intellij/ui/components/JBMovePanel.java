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
package com.intellij.ui.components;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.Map;

/**
 * A UI control which consists of two lists with ability to move elements between them.
 * <p/>
 * It looks as <a href="http://openfaces.org/documentation/developersGuide/twolistselection.html">here</a>.
 * 
 * @author Konstantin Bulenkov
 */
public class JBMovePanel extends JBPanel {

  public static final String MOVE_PANEL_PLACE = "MOVE_PANEL";

  public static final InsertPositionStrategy ANCHORING_SELECTION = new InsertPositionStrategy() {
    @Override
    public int getInsertionIndex(@NotNull Object data, @NotNull JList list) {
      int index = list.getSelectedIndex();
      DefaultListModel model = (DefaultListModel)list.getModel();
      return index < 0 ? model.getSize() : index + 1;
    }
  };

  public static final InsertPositionStrategy NATURAL_ORDER = new InsertPositionStrategy() {
    @SuppressWarnings("unchecked")
    @Override
    public int getInsertionIndex(@NotNull Object data, @NotNull JList list) {
      Enumeration elements = ((DefaultListModel)list.getModel()).elements();
      int index = 0;
      while (elements.hasMoreElements()) {
        Object e = elements.nextElement();
        // DefaultListModel is type-aware only since java7, so, use raw types until we're on java6.
        if (((Comparable)e).compareTo(data) >= 0) {
          break;
        }
        index++;
      }
      return index;
    }
  };

  @NotNull private final Map<ButtonType, ActionButton> myButtons = new EnumMap<>(ButtonType.class);

  @NotNull private final ListPanel myLeftPanel  = new ListPanel();
  @NotNull private final ListPanel myRightPanel = new ListPanel();

  @NotNull protected final JList        myLeftList;
  @NotNull protected final JList        myRightList;
  @NotNull protected final ActionButton myLeftButton;
  @NotNull protected final ActionButton myAllLeftButton;
  @NotNull protected final ActionButton myRightButton;
  @NotNull protected final ActionButton myAllRightButton;
  @NotNull protected final ActionButton myUpButton;
  @NotNull protected final ActionButton myDownButton;

  @NotNull private InsertPositionStrategy myLeftInsertionStrategy = ANCHORING_SELECTION;
  @NotNull private InsertPositionStrategy myRightInsertionStrategy = ANCHORING_SELECTION;

  private boolean myActivePreferredSizeProcessing;

  public enum ButtonType {LEFT, RIGHT, ALL_LEFT, ALL_RIGHT}

  public JBMovePanel(@NotNull JList left, @NotNull JList right) {
    super(new GridBagLayout());
    assertModelIsEditable(left);
    assertModelIsEditable(right);
    myLeftList = left;
    myRightList = right;

    final JPanel leftRightButtonsPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.MIDDLE));
    leftRightButtonsPanel.add(myRightButton = createButton(ButtonType.RIGHT));
    leftRightButtonsPanel.add(myAllRightButton = createButton(ButtonType.ALL_RIGHT));
    leftRightButtonsPanel.add(myLeftButton = createButton(ButtonType.LEFT));
    leftRightButtonsPanel.add(myAllLeftButton = createButton(ButtonType.ALL_LEFT));

    myUpButton = createButton(new UpAction());
    myDownButton = createButton(new DownAction());
    final JPanel upDownButtonsPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.MIDDLE));
    upDownButtonsPanel.add(myUpButton);
    upDownButtonsPanel.add(myDownButton);

    MouseListener mouseListener = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() != 2 || e.getButton() != MouseEvent.BUTTON1) {
          return;
        }
        if (e.getSource() == myLeftList) {
          doRight();
        }
        else if (e.getSource() == myRightList) {
          doLeft();
        }
      }
    };
    myLeftList.addMouseListener(mouseListener);
    myRightList.addMouseListener(mouseListener);

    GridBag listConstraints = new GridBag().weightx(1).weighty(1).fillCell();
    GridBag buttonConstraints = new GridBag().anchor(GridBagConstraints.CENTER);
    myLeftPanel.add(ScrollPaneFactory.createScrollPane(left), listConstraints);
    add(myLeftPanel, listConstraints);
    add(leftRightButtonsPanel, buttonConstraints);
    myRightPanel.add(ScrollPaneFactory.createScrollPane(right), listConstraints);
    add(myRightPanel, listConstraints);
    add(upDownButtonsPanel, buttonConstraints);
  }

  private static void assertModelIsEditable(@NotNull JList list) {
    assert list.getModel() instanceof DefaultListModel : String
      .format("List model should extends %s interface", DefaultListModel.class.getName());
  }

  public void setShowButtons(@NotNull ButtonType... types) {
    for (ActionButton button : myButtons.values()) {
      button.setVisible(false);
    }
    for (ButtonType type : types) {
      myButtons.get(type).setVisible(true);
    }
  }

  public void setListLabels(@NotNull String left, @NotNull String right) {
    // Border insets are used as a component insets (see JComponent.getInsets()). That's why an ugly bottom inset is used when
    // we create a border with default insets. That is the reason why we explicitly specify bottom inset as zero.
    Insets insets = new Insets(IdeBorderFactory.TITLED_BORDER_TOP_INSET,
                               IdeBorderFactory.TITLED_BORDER_LEFT_INSET,
                               0,
                               IdeBorderFactory.TITLED_BORDER_RIGHT_INSET);
    myLeftPanel.setBorder(IdeBorderFactory.createTitledBorder(left, false, insets));
    myRightPanel.setBorder(IdeBorderFactory.createTitledBorder(right, false, insets));
  }

  public void setLeftInsertionStrategy(@NotNull InsertPositionStrategy leftInsertionStrategy) {
    myLeftInsertionStrategy = leftInsertionStrategy;
  }
  
  // Commented to preserve green code policy until this method is not used. Uncomment when necessary.
  //public void setRightInsertionStrategy(@NotNull InsertPositionStrategy rightInsertionStrategy) {
  //  myRightInsertionStrategy = rightInsertionStrategy;
  //}
  
  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myLeftList.setEnabled(enabled);
    myRightList.setEnabled(enabled);
    for (ActionButton button : myButtons.values()) {
      button.setEnabled(enabled);
    }
  }

  @NotNull
  private ActionButton createButton(@NotNull final ButtonType type) {
    final AnAction action;
    switch (type) {
      case LEFT:
        action = new LeftAction();
        break;
      case RIGHT:
        action = new RightAction();
        break;
      case ALL_LEFT:
        action = new AllLeftAction();
        break;
      case ALL_RIGHT:
        action = new AllRightAction();
        break;
      default: throw new IllegalArgumentException("Unsupported button type: " + type);
    }


    ActionButton button = createButton(action);
    myButtons.put(type, button);
    return button;
  }

  @NotNull
  private static ActionButton createButton(@NotNull final AnAction action) {
    PresentationFactory presentationFactory = new PresentationFactory();
    Icon icon = AllIcons.Actions.AllLeft;
    Dimension size = new Dimension(icon.getIconWidth(), icon.getIconHeight());
    return new ActionButton(action, presentationFactory.getPresentation(action), MOVE_PANEL_PLACE, size);
  }

  protected void doRight() {
    moveBetween(myRightList, myRightInsertionStrategy, myLeftList);
  }

  protected void doLeft() {
    moveBetween(myLeftList, myLeftInsertionStrategy, myRightList);
  }

  protected void doAllLeft() {
    moveAllBetween(myLeftList, myRightList);
  }

  protected void doAllRight() {
    moveAllBetween(myRightList, myLeftList);
  }

  private static void moveBetween(@NotNull JList to, @NotNull InsertPositionStrategy strategy, @NotNull JList from) {
    final int[] indices = from.getSelectedIndices();
    if (indices.length <= 0) {
      return;
    }
    
    final Object[] values = from.getSelectedValues();
    for (int i = indices.length - 1; i >= 0; i--) {
      ((DefaultListModel)from.getModel()).remove(indices[i]);
    }
    if (from.getModel().getSize() > 0) {
      int newSelectionIndex = indices[0];
      newSelectionIndex = Math.min(from.getModel().getSize() - 1, newSelectionIndex);
      from.setSelectedIndex(newSelectionIndex);
    }

    to.clearSelection();
    DefaultListModel toModel = (DefaultListModel)to.getModel();
    int newSelectionIndex = -1;
    for (Object value : values) {
      if (!toModel.contains(value)) {
        int i = strategy.getInsertionIndex(value, to);
        if (newSelectionIndex < 0) {
          newSelectionIndex = i;
        }
        toModel.add(i, value);
        to.addSelectionInterval(i, i);
      }
    }
  }

  private static void moveAllBetween(@NotNull JList to, @NotNull JList from) {
    final DefaultListModel fromModel = (DefaultListModel)from.getModel();
    final DefaultListModel toModel = (DefaultListModel)to.getModel();
    while (fromModel.getSize() > 0) {
      Object element = fromModel.remove(0);
      if (!toModel.contains(element)) {
        toModel.addElement(element);
      }
    }
  }

  public static void main(String[] args) {
    final JBMovePanel panel = new JBMovePanel(new JBList("asdas", "weqrwe", "ads12312", "aZSD23"),
                                              new JBList("123412", "as2341", "aaaaaaaaaaa", "ZZZZZZZZZZ", "12"));
    final JFrame test = new JFrame("Test");
    test.setContentPane(panel);
    test.setSize(500, 500);
    test.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    test.setVisible(true);
  }
  
  public interface InsertPositionStrategy {
    int getInsertionIndex(@NotNull Object data, @NotNull JList list);
  }

  /**
   * The general idea is to layout target lists to use the same width. This wrapper panel controls that.
   */
  private class ListPanel extends JPanel {

    ListPanel() {
      super(new GridBagLayout());
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension d1 = super.getPreferredSize();
      if (myActivePreferredSizeProcessing) {
        return d1;
      }
      myActivePreferredSizeProcessing = true;
      try {
        final Dimension d2;
        if (myLeftPanel == this) {
          d2 = myRightPanel.getPreferredSize();
        }
        else {
          d2 = myLeftPanel.getPreferredSize();
        }
        return new Dimension(Math.max(d1.width, d2.width), Math.max(d1.height, d2.height));
      }
      finally {
        myActivePreferredSizeProcessing = false;
      }
    }
  }
  
  private class LeftAction extends AnAction {

    LeftAction() {
      getTemplatePresentation().setIcon(AllIcons.Actions.Left);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      doLeft(); 
    }
  }

  private class RightAction extends AnAction {

    RightAction() {
      getTemplatePresentation().setIcon(AllIcons.Actions.Right);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      doRight();
    }
  }

  private class AllLeftAction extends AnAction {

    AllLeftAction() {
      getTemplatePresentation().setIcon(AllIcons.Actions.AllLeft);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      doAllLeft();
    }
  }

  private class AllRightAction extends AnAction {

    AllRightAction() {
      getTemplatePresentation().setIcon(AllIcons.Actions.AllRight);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      doAllRight();
    }
  }
  
  private class UpAction extends AnAction {
    
    UpAction() {
      getTemplatePresentation().setIcon(AllIcons.Actions.UP);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      ListUtil.moveSelectedItemsUp(myRightList); 
    }
  }

  private class DownAction extends AnAction {

    DownAction() {
      getTemplatePresentation().setIcon(AllIcons.Actions.Down);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      ListUtil.moveSelectedItemsDown(myRightList);
    }
  }
}
