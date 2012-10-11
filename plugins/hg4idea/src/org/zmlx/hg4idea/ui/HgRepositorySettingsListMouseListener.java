// Copyright Robin Stevens
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
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
  private WeakReference<AbstractButton> myButton = new WeakReference<AbstractButton>(null);
  @Override
  public void mousePressed(MouseEvent e) {
    //remember on which button was mouse was originally pressed
    super.mousePressed(e);
    if ( e.getButton() == MouseEvent.BUTTON1 ){
      myButton = new WeakReference<AbstractButton>( getButton(e));
    }
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    //only react on left mouse button clicks and only when the mouse is released at the same button
    //where it was pressed
    if ( e.getButton() == MouseEvent.BUTTON1 ){
      AbstractButton currentButton = getButton(e);
      AbstractButton previousButton = myButton.get();
      if ( currentButton != null && currentButton == previousButton ){
        currentButton.doClick();
      }
    }
    myButton = new WeakReference<AbstractButton>(null);
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
