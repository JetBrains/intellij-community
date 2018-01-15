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
package com.intellij.internal.focus;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusEvent;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * @author spleaner
 */
public class FocusDebuggerAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.internal.focus.FocusDebuggerAction");
  private FocusDrawer myFocusDrawer;

  public FocusDebuggerAction() {
    if (Boolean.getBoolean("idea.ui.debug.mode")) {
      ApplicationManager.getApplication().invokeLater(() -> actionPerformed(null));
    }
  }

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

  private static class FocusDrawer extends Thread implements AWTEventListener, ApplicationActivationListener {
    private Component myCurrent;
    private Component myPrevious;
    private boolean myTemporary;

    private boolean myRunning = true;

    enum ApplicationState {
      ACTIVE, DELAYED, INACTIVE, UNKNOWN;

      public Color getColor() {
        switch (this) {
          case ACTIVE:
            return JBColor.green;
          case DELAYED:
            return JBColor.yellow;
          case INACTIVE:
            return JBColor.red;
          case UNKNOWN:
            return JBColor.gray;
        }
        throw new RuntimeException("Unknown application state");
      }
    }

    private ApplicationState myApplicationState = ApplicationState.UNKNOWN;

    public FocusDrawer() {
      super("focus debugger");
      Application app = ApplicationManager.getApplication();
      app.getMessageBus().connect().subscribe(ApplicationActivationListener.TOPIC, this);
    }

    public void applicationActivated(IdeFrame ideFrame) {
      myApplicationState = ApplicationState.ACTIVE;
    }

    public void applicationDeactivated(IdeFrame ideFrame) {
      myApplicationState = ApplicationState.INACTIVE;
    }

    public void delayedApplicationDeactivated(IdeFrame ideFrame) {
      myApplicationState = ApplicationState.DELAYED;
    }

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
        Graphics2D currentFocusGraphics = (Graphics2D)(myCurrent.getGraphics() != null ? myCurrent.getGraphics().create() : null);
        try {
          if (currentFocusGraphics != null) {
            if (clean) {
              if (myCurrent.isDisplayable()) {
                myCurrent.repaint();
              }
            }
            else {
              currentFocusGraphics.setStroke(new BasicStroke(JBUI.scale(1)));
              currentFocusGraphics.setColor(myTemporary ? JBColor.ORANGE : JBColor.GREEN);
              UIUtil.drawDottedRectangle(currentFocusGraphics, 1, 1, myCurrent.getSize().width - 2, myCurrent.getSize().height - 2);
            }
          }
        } finally {
          if (currentFocusGraphics != null) currentFocusGraphics.dispose();
        }
        if (myPrevious != null) {
          Graphics2D previousFocusGraphics = (Graphics2D)(myPrevious.getGraphics() != null ? myPrevious.getGraphics().create() : null);
          try {
            if (previousFocusGraphics != null) {
              if (clean) {
                if (myPrevious.isDisplayable()) {
                  myPrevious.repaint();
                }
              }
              else {
                previousFocusGraphics.setStroke(new BasicStroke(JBUI.scale(1)));
                previousFocusGraphics.setColor(JBColor.RED);
                UIUtil.drawDottedRectangle(previousFocusGraphics, 1, 1, myPrevious.getSize().width - 2, myPrevious.getSize().height - 2);
              }
            }
          }
          finally {
            if (previousFocusGraphics != null) previousFocusGraphics.dispose();
          }
        }
        drawOnGraphics(g -> {
          g.setColor(myApplicationState.getColor());
          g.fillOval(5,5, 10, 10);
          g.setColor(JBColor.black);
          g.setStroke(new BasicStroke(2));
          g.drawOval(5,5, 10, 10);
        });
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
        drawOnGraphics(g -> {
          g.setColor(myApplicationState.getColor());
          g.fillOval(5,5, 10, 10);
          g.setColor(JBColor.black);
          g.setStroke(new BasicStroke(2));
          g.drawOval(5,5, 10, 10);
          g.setColor(myApplicationState.getColor());
          g.fillOval(5,5, 10, 10);
        });
        Arrays.stream(Window.getOwnerlessWindows()).
          filter(window -> window != null && window instanceof RootPaneContainer).
          map(window -> (RootPaneContainer)window).
          filter(f -> f.getRootPane() != null).
          filter(window -> window.getRootPane() != null).
          map(window -> (window).getGlassPane()).
          map(jGlassPane -> jGlassPane.getGraphics()).
          filter(g -> g != null).
          forEach(graphics -> {
            Graphics2D glassPaneGraphics = ((Graphics2D)graphics.create());
            try {

            } finally {
              glassPaneGraphics.dispose();
            }
          });
      }
    }

    private void drawOnGraphics(Consumer<Graphics2D> consumer) {
      Arrays.stream(Frame.getFrames()).
        filter(window -> window != null && window instanceof RootPaneContainer).
        map(window -> (RootPaneContainer)window).
        filter(w -> w instanceof JFrame).
        filter(f -> f.getRootPane() != null).
        filter(f -> f.getGlassPane() != null).
        filter(window -> window.getRootPane() != null).
        map(window -> (window).getGlassPane()).
        map(jGlassPane -> jGlassPane.getGraphics()).
        filter(g -> g != null).
        forEach(graphics -> {
          Graphics glassPaneGraphics = graphics.create();
          try {
            consumer.accept((Graphics2D)glassPaneGraphics);
          } finally {
            glassPaneGraphics.dispose();
          }
        });
    }
  }
}

