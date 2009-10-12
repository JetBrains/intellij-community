
/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ui.plaf.beg;

import java.awt.event.MouseEvent;
import javax.swing.*;
import javax.swing.event.MouseInputListener;
import javax.swing.plaf.basic.BasicListUI;

public class BegListUI extends BasicListUI {
  protected MouseInputListener createMouseInputListener() {
    return new PatchedInputHandler(this.list);
  }

  protected int convertYToRow(int y) {
    return super.convertYToRow(y);
  }

  public class PatchedInputHandler extends MouseInputHandler {
    private final JList myList;

    PatchedInputHandler(JList list) {
      myList = list;
    }

    public void mousePressed(MouseEvent e) {
      if (!myList.isEnabled()){
        return;
      }

      /* Request focus before updating the list selection.  This implies
       * that the current focus owner will see a focusLost() event
       * before the lists selection is updated IF requestFocus() is
       * synchronous (it is on Windows).  See bug 4122345
       */
      if (!myList.hasFocus()){
        myList.requestFocus();
      }

      int row = BegListUI.this.convertYToRow(e.getY());
      if (row != -1){
        myList.setValueIsAdjusting(true);
        int anchorIndex = myList.getAnchorSelectionIndex();
        if (e.isControlDown()){
          if (myList.isSelectedIndex(row)){
            myList.removeSelectionInterval(row, row);
          }
          else{
            myList.addSelectionInterval(row, row);
          }
        }
        else
          if (e.isShiftDown() && (anchorIndex != -1)){
            myList.setSelectionInterval(anchorIndex, row);
          }
          else{
            myList.setSelectionInterval(row, row);
          }
      }
    }

    public void mouseReleased(MouseEvent e) {
      myList.setValueIsAdjusting(false);
    }
  }
}
