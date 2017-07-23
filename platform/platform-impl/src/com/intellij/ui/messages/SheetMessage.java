/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.apple.eawt.FullScreenUtilities;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.mac.MacMainFrameDecorator;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.Animator;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance;

/**
 * Created by Denis Fokin
 */
public class SheetMessage implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.messages.SheetMessage");

  private final JDialog myWindow;
  private final Window myParent;
  private final SheetController myController;

  private final static int TIME_TO_SHOW_SHEET = 250;

  private Image staticImage;
  private int imageHeight;

  public SheetMessage(final Window owner,
                      final String title,
                      final String message,
                      final Icon icon,
                      final String[] buttons,
                      final DialogWrapper.DoNotAskOption doNotAskOption,
                      final String defaultButton,
                      final String focusedButton)
  {
    final Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    final Component recentFocusOwner = activeWindow == null ? null : activeWindow.getMostRecentFocusOwner();
    WeakReference<Component> beforeShowFocusOwner = new WeakReference<>(recentFocusOwner);

    maximizeIfNeeded(owner);

    myWindow = new JDialog(owner, "This should not be shown", Dialog.ModalityType.APPLICATION_MODAL);
    myWindow.getRootPane().putClientProperty("apple.awt.draggableWindowBackground", Boolean.FALSE);

    WindowAdapter windowListener = new WindowAdapter() {
      @Override
      public void windowActivated(@NotNull WindowEvent e) {
        super.windowActivated(e);
      }
    };
    myWindow.addWindowListener(windowListener);
    Disposer.register(this, () -> myWindow.removeWindowListener(windowListener));

    myParent = owner;

    myWindow.setUndecorated(true);
    myWindow.setBackground(Gray.TRANSPARENT);
    myController = new SheetController(this, title, message, icon, buttons, defaultButton, doNotAskOption, focusedButton);
    Disposer.register(this, myController);

    imageHeight = 0;
    ComponentAdapter componentAdapter = new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent event) {
        setPositionRelativeToParent();
      }

      @Override
      public void componentMoved(ComponentEvent event) {
        setPositionRelativeToParent();
      }
    };
    myParent.addComponentListener(componentAdapter);
    Disposer.register(this, () -> myParent.removeComponentListener(componentAdapter));
    myWindow.setFocusable(true);
    myWindow.setFocusableWindowState(true);
    if (SystemInfo.isJavaVersionAtLeast("1.7")) {
      myWindow.setSize(myController.SHEET_NC_WIDTH, 0);

      setWindowOpacity(0.0f);

      ComponentAdapter componentListener = new ComponentAdapter() {
        @Override
        public void componentShown(@NotNull ComponentEvent e) {
          super.componentShown(e);
          setWindowOpacity(1.0f);
          myWindow.setSize(myController.SHEET_NC_WIDTH, myController.SHEET_NC_HEIGHT);
        }
      };
      myWindow.addComponentListener(componentListener);
      Disposer.register(this, () -> myWindow.removeComponentListener(componentListener));
    } else {
      myWindow.setModal(true);
      myWindow.setSize(myController.SHEET_NC_WIDTH, myController.SHEET_NC_HEIGHT);
      setPositionRelativeToParent();
    }

    KeyListener animationKeyListener = new KeyListener() {
      @Override
      public void keyTyped(KeyEvent e) {}

      @Override
      public void keyPressed(KeyEvent e) {
        int modifiers = e.getModifiers();
        int modifiersUnion = InputEvent.SHIFT_DOWN_MASK
                             | InputEvent.CTRL_DOWN_MASK
                             | InputEvent.ALT_DOWN_MASK
                             | InputEvent.META_DOWN_MASK;

        boolean modifiersAreNotPressed = ((modifiers & modifiersUnion) == 0);

        if (modifiersAreNotPressed) {
          if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            Disposer.dispose(SheetMessage.this);
          }
          if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            myController.setDefaultResult();
            Disposer.dispose(SheetMessage.this);
          }
          if (e.getKeyCode() == KeyEvent.VK_SPACE) {
            myController.setFocusedResult();
            Disposer.dispose(SheetMessage.this);
          }
        }
      }

      @Override
      public void keyReleased(KeyEvent e) {}
    };
    Disposer.register(this, () -> myWindow.removeKeyListener(animationKeyListener));
    myWindow.addKeyListener(animationKeyListener);

    startAnimation(true);
    if (couldBeInFullScreen()) {
      FullScreenUtilities.setWindowCanFullScreen(myParent, false);
      Disposer.register(this, () -> FullScreenUtilities.setWindowCanFullScreen(myParent, true));
    }

    LaterInvocator.enterModal(myWindow);
    myWindow.setVisible(true);
    LaterInvocator.leaveModal(myWindow);

    Component focusCandidate = beforeShowFocusOwner.get();

    if (focusCandidate == null) {
      focusCandidate = getGlobalInstance().getLastFocusedFor(getGlobalInstance().getLastFocusedFrame());
    }

    final Component finalFocusCandidate = focusCandidate;

    // focusCandidate is null if a welcome screen is closed and ide frame is not opened.
    // this is ok. We set focus correctly on our frame activation.
    if (focusCandidate != null) {
      getGlobalInstance().doWhenFocusSettlesDown(() -> getGlobalInstance().requestFocus(finalFocusCandidate, true));
    }
  }

  @Override
  public void dispose() {
    DialogWrapper.cleanupRootPane(myWindow.getRootPane());
    myWindow.dispose();
  }

  private static void maximizeIfNeeded(final Window owner) {
    if (owner == null) return;
    if (owner instanceof Frame) {
      Frame f = (Frame)owner;
      if (f.getState() == Frame.ICONIFIED) {
        f.setState(Frame.NORMAL);
      }
    }
  }

  private void setWindowOpacity(float opacity) {
    try {
      Method setOpacityMethod = myWindow.getClass().getMethod("setOpacity", Float.TYPE);
      setOpacityMethod.invoke(myWindow, opacity);
    }
    catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      LOG.error(e);
    }
  }

  private boolean couldBeInFullScreen() {
    if (myParent instanceof JFrame) {
      JRootPane rootPane = ((JFrame)myParent).getRootPane();
      return rootPane.getClientProperty(MacMainFrameDecorator.FULL_SCREEN) == null;
    }
    return false;
  }

  public boolean toBeShown() {
    return !myController.getDoNotAskResult();
  }

  public String getResult() {
    return myController.getResult();
  }

  void startAnimation(final boolean enlarge) {
    staticImage = myController.getStaticImage();
    JPanel staticPanel = new JPanel() {
      @Override
      public void paint(@NotNull Graphics g) {
        super.paint(g);
        if (staticImage != null) {
          Graphics2D g2d = (Graphics2D) g.create();


          g2d.setBackground(new JBColor(new Color(255, 255, 255, 0), new Color(110, 110, 110, 0)));
          g2d.clearRect(0, 0, myController.SHEET_NC_WIDTH, myController.SHEET_NC_HEIGHT);


          g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.95f));

          int multiplyFactor = staticImage.getWidth(null)/myController.SHEET_NC_WIDTH;

          g.drawImage(staticImage, 0, 0,
                      myController.SHEET_NC_WIDTH, imageHeight,
                      0, staticImage.getHeight(null) - imageHeight * multiplyFactor,
                      staticImage.getWidth(null), staticImage.getHeight(null),
                      null);
        }
      }
    };
    staticPanel.setOpaque(false);
    staticPanel.setSize(myController.SHEET_NC_WIDTH,myController.SHEET_NC_HEIGHT);
    myWindow.setContentPane(staticPanel);

    Animator myAnimator = new Animator("Roll Down Sheet Animator", myController.SHEET_NC_HEIGHT ,
                                       TIME_TO_SHOW_SHEET, false) {
      @Override
      public void paintNow(int frame, int totalFrames, int cycle) {
        setPositionRelativeToParent();
        float percentage = (float)frame/(float)totalFrames;
        imageHeight = enlarge ? (int)(((float)myController.SHEET_NC_HEIGHT) * percentage):
                      (int)(myController.SHEET_NC_HEIGHT - percentage * myController.SHEET_HEIGHT);
        myWindow.repaint();
      }

      @Override
      protected void paintCycleEnd() {
        setPositionRelativeToParent();
        if (enlarge) {
          imageHeight = myController.SHEET_NC_HEIGHT;
          staticImage = null;
          myWindow.setContentPane(myController.getPanel(myWindow));

          IJSwingUtilities.moveMousePointerOn(myWindow.getRootPane().getDefaultButton());
          myController.requestFocus();
        } 
        else {
          Disposer.dispose(SheetMessage.this);
        }
      }
    };
    Disposer.register(this, myAnimator);
    myAnimator.resume();
  }

  private void setPositionRelativeToParent () {
    int width = myParent.getWidth();
    myWindow.setBounds(width / 2 - myController.SHEET_NC_WIDTH / 2 + myParent.getLocation().x,
                       myParent.getInsets().top + myParent.getLocation().y,
                       myController.SHEET_NC_WIDTH,
                       myController.SHEET_NC_HEIGHT);

  }
}
