
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.plaf.beg;

import com.intellij.openapi.wm.IdeFocusManager;

import javax.swing.*;
import javax.swing.event.MouseInputListener;
import javax.swing.plaf.basic.BasicListUI;
import java.awt.event.MouseEvent;

public class BegListUI extends BasicListUI {
  @Override
  protected MouseInputListener createMouseInputListener() {
    return new PatchedInputHandler(this.list);
  }

  public class PatchedInputHandler extends MouseInputHandler {
    private final JList myList;

    PatchedInputHandler(JList list) {
      myList = list;
    }

    @Override
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
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
          IdeFocusManager.getGlobalInstance().requestFocus(myList, true);
        });
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

    @Override
    public void mouseReleased(MouseEvent e) {
      myList.setValueIsAdjusting(false);
    }
  }
}
