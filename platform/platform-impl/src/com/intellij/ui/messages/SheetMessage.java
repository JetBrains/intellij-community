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
package com.intellij.ui.messages;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;


/**
 * Created by Denis Fokin
 */
public class SheetMessage  implements ActionListener {
  private JDialog myWindow;
  private Window myParent;
  private SheetController myController;
  private Timer myAnimator = new Timer(2, this);

  private boolean myShouldEnlarge = true;

  private final static int SHEET_ANIMATION_STEP = 4;

  public SheetMessage(final Window owner,
                      final String title,
                      final String message,
                      final Icon icon,
                      final String[] buttons,
                      final DialogWrapper.DoNotAskOption doNotAskOption,
                      final String focusedButton,
                      final String defaultButton)
  {
    myWindow = new JDialog(owner, "This should not be shown", Dialog.ModalityType.APPLICATION_MODAL) ;
    myParent = owner;
    myWindow.setSize(SheetController.SHEET_WIDTH, 0);
    myWindow.setUndecorated(true);
    myWindow.setBackground(new JBColor(new Color(0, 0, 0, 0), new Color(0, 0, 0, 0)));
    myController = new SheetController(this, title, message, icon, buttons, defaultButton, doNotAskOption, focusedButton);
    myWindow.setContentPane(myController.getStaticPanel());
    registerMoveResizeHandler();
    myWindow.setFocusableWindowState(true);
    myWindow.setFocusable(true);

    myAnimator.start();
    myWindow.setVisible(true);
  }

  public boolean toBeShown() {
    return !myController.getDoNotAskResult();
  }

  public String getResult() {
    return myController.getResult();
  }

  void startAnimation () {
    myWindow.setContentPane(myController.getStaticPanel());
    myAnimator.start();
  }



  @Override
  public void actionPerformed(ActionEvent e) {

    int windowHeight = (myShouldEnlarge) ? myWindow.getHeight() + SHEET_ANIMATION_STEP
                                         : myWindow.getHeight() - SHEET_ANIMATION_STEP;

    myWindow.setSize(myWindow.getWidth(), windowHeight);
    setPositionRelativeToParent();
    if (myWindow.getHeight() > myController.SHEET_HEIGHT) {
      myAnimator.stop();
      myWindow.setContentPane(
        myController.getPanel(myWindow)
      );
      myController.requestFocus();
      myShouldEnlarge = false;
    }

    if (myWindow.getHeight() < 0) {
      myAnimator.stop();
      myWindow.dispose();
    }
  }

  private void setPositionRelativeToParent () {
    int width = myParent.getWidth();
    myWindow.setLocation(width / 2 - SheetController.SHEET_WIDTH / 2 + myParent.getLocation().x, myParent.getInsets().top
                                                                                                 + myParent.getLocation().y);
  }

  private void registerMoveResizeHandler () {
    myParent.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        super.componentResized(e);
        setPositionRelativeToParent();
      }

      @Override
      public void componentMoved(ComponentEvent e) {
        super.componentMoved(e);
        setPositionRelativeToParent();
      }
    });
  }
}



