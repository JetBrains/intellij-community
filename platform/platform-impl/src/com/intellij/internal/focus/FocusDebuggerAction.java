// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusEvent;

/**
 * @author spleaner
 */
public class FocusDebuggerAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.internal.focus.FocusDebuggerAction");
  private FocusDrawer myFocusDrawer;

  public FocusDebuggerAction() {
    if (Boolean.getBoolean("idea.ui.debug.mode")) {
      ApplicationManager.getApplication().invokeLater(() -> perform());
    }
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    perform();
  }

  private void perform() {
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
  public void update(@NotNull final AnActionEvent e) {
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

    FocusDrawer() {
      super("focus debugger");
      Application app = ApplicationManager.getApplication();
      app.getMessageBus().connect().subscribe(ApplicationActivationListener.TOPIC, this);
    }

    @Override
    public void applicationActivated(@NotNull IdeFrame ideFrame) {
      myApplicationState = ApplicationState.ACTIVE;
    }

    @Override
    public void applicationDeactivated(@NotNull IdeFrame ideFrame) {
      myApplicationState = ApplicationState.INACTIVE;
    }

    @Override
    public void delayedApplicationDeactivated(@NotNull IdeFrame ideFrame) {
      myApplicationState = ApplicationState.DELAYED;
    }

    public void setRunning(final boolean running) {
      myRunning = running;
    }

    public boolean isRunning() {
      return myRunning;
    }

    @Override
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
        Util.drawOnActiveFrameGraphics(g -> {
          g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          g.setColor(myApplicationState.getColor());
          g.fillOval(5,5, 10, 10);
          g.setColor(JBColor.black);
          g.setStroke(new BasicStroke(2));
          g.drawOval(5,5, 10, 10);
        });
      }
    }

    @Override
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
        Util.drawOnActiveFrameGraphics(g -> {
          g.setColor(myApplicationState.getColor());
          g.fillOval(5,5, 10, 10);
          g.setColor(JBColor.black);
          g.setStroke(new BasicStroke(2));
          g.drawOval(5,5, 10, 10);
          g.setColor(myApplicationState.getColor());
          g.fillOval(5,5, 10, 10);
        });
      }
    }
  }
}

