package org.zmlx.hg4idea.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;

/**
 * {@code MouseListener} which performs a click on the {@code JCheckBox} rendered
 * in the list
 */
final class HgRepositorySettingsListMouseListener extends MouseAdapter{
  private WeakReference<AbstractButton> button = new WeakReference<AbstractButton>(null);
  @Override
  public void mousePressed(MouseEvent e) {
    //remember on which button was mouse was originally pressed
    super.mousePressed(e);
    if ( e.getButton() == MouseEvent.BUTTON1 ){
      button = new WeakReference<AbstractButton>( getButton(e));
    }
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    //only react on left mouse button clicks and only when the mouse is released at the same button
    //where it was pressed
    if ( e.getButton() == MouseEvent.BUTTON1 ){
      AbstractButton currentButton = getButton(e);
      AbstractButton previousButton = button.get();
      if ( currentButton != null && currentButton == previousButton ){
        currentButton.doClick();
      }
    }
    button = new WeakReference<AbstractButton>(null);
  }

  private static AbstractButton getButton(MouseEvent e){
    JList list = (JList)e.getSource();

    int rowIndex = list.locationToIndex(e.getPoint());
    if ( rowIndex != -1 ){
      Rectangle rowBounds = list.getCellBounds(rowIndex, rowIndex);
      Component rendererComponent =
        list.getCellRenderer().getListCellRendererComponent(list, list.getModel().getElementAt(rowIndex), rowIndex, true, false);
      rendererComponent.setBounds(rowBounds);
      refreshLayout(rendererComponent);

      Component componentUnderMouseClick =
        SwingUtilities.getDeepestComponentAt(rendererComponent, e.getX() - rowBounds.x, e.getY() - rowBounds.y);
      if ( componentUnderMouseClick instanceof AbstractButton ){
        return (AbstractButton)componentUnderMouseClick;
      }
    }
    return null;
  }

  private static void refreshLayout(Component component){
    component.doLayout();
    if ( component instanceof Container ){
      Component[] components = ((Container)component).getComponents();
      for (Component childComponent : components) {
        refreshLayout(childComponent);
      }
    }
  }
}
