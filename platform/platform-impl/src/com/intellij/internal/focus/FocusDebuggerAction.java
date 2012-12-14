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
package com.intellij.internal.focus;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;

import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusEvent;

/**
 * @author spleaner
 */
public class FocusDebuggerAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.internal.focus.FocusDebuggerAction");
  private FocusDrawer myFocusDrawer;

  public void actionPerformed(final AnActionEvent e) {
    if (myFocusDrawer == null) {
      myFocusDrawer = new FocusDrawer();
      myFocusDrawer.start();
      Toolkit.getDefaultToolkit().addAWTEventListener(myFocusDrawer, AWTEvent.FOCUS_EVENT_MASK);
    } else {
      myFocusDrawer.setRunning(false);
      Toolkit.getDefaultToolkit().removeAWTEventListener(myFocusDrawer);
      myFocusDrawer = null;
    }
  }

  @Override
  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    if (myFocusDrawer == null) {
      presentation.setText("Start Focus Debugger");
    } else {
      presentation.setText("Stop Focus Debugger");
    }
  }

  private static class FocusDrawer extends Thread implements AWTEventListener {
    private Component myCurrent;
    private Component myPrevious;
    private boolean myTemporary;

    private boolean myRunning = true;

    public void setRunning(final boolean running) {
      myRunning = running;
    }

    public boolean isRunning() {
      return myRunning;
    }

    public void run() {
      try {
        while (myRunning) {
          paintFocusBorders(false);
          Thread.sleep(100);
        }

        paintFocusBorders(true);
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
    }

    private void paintFocusBorders(boolean clean) {
      if (myCurrent != null) {
        Graphics currentFocusGraphics = myCurrent.getGraphics();
        if (currentFocusGraphics != null) {
          if (clean) {
            if (myCurrent.isDisplayable()) {
              myCurrent.repaint();
            }
          } else {
            currentFocusGraphics.setColor(myTemporary ? JBColor.ORANGE : JBColor.GREEN);
            UIUtil.drawDottedRectangle(currentFocusGraphics, 1, 1, myCurrent.getSize().width - 2, myCurrent.getSize().height - 2);
          }
        }
      }

      if (myPrevious != null) {
        Graphics previousFocusGraphics = myPrevious.getGraphics();
        if (previousFocusGraphics != null) {
          if (clean) {
            if (myPrevious.isDisplayable()) {
              myPrevious.repaint();
            }
          } else {
            previousFocusGraphics.setColor(JBColor.RED);
            UIUtil.drawDottedRectangle(previousFocusGraphics, 1, 1, myPrevious.getSize().width - 2, myPrevious.getSize().height - 2);
          }
        }
      }
    }

    public void eventDispatched(AWTEvent event) {
      if (event instanceof FocusEvent) {
        FocusEvent focusEvent = (FocusEvent)event;
        Component fromComponent = focusEvent.getComponent();
        Component oppositeComponent = focusEvent.getOppositeComponent();

        paintFocusBorders(true);

        switch (event.getID()) {
          case FocusEvent.FOCUS_GAINED:
            myCurrent = fromComponent;
            myPrevious = oppositeComponent;
            myTemporary = focusEvent.isTemporary();
            break;
          case FocusEvent.FOCUS_LOST:
            myTemporary = focusEvent.isTemporary();
          default:
            break;
        }
      }
    }
  }
}

