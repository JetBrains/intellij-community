// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.messages;

import com.apple.eawt.FullScreenUtilities;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.mac.MacMainFrameDecorator;
import com.intellij.ui.mac.touchbar.TouchBarsManager;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.Animator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.ref.WeakReference;

import static com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance;

/**
 * Created by Denis Fokin
 */
final class SheetMessage implements Disposable {
  private final JDialog myWindow;
  private final Window myParent;
  private final SheetController myController;

  private static final int TIME_TO_SHOW_SHEET = 250;

  private Image staticImage;
  private int imageHeight;

  SheetMessage(@NotNull Window owner,
               @Nls String title,
               @Nls String message,
               Icon icon,
               String[] buttons,
               DialogWrapper.DoNotAskOption doNotAskOption,
               String defaultButton,
               String focusedButton) {
    myParent = owner;

    Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    Component recentFocusOwner = activeWindow == null ? null : activeWindow.getMostRecentFocusOwner();
    WeakReference<Component> beforeShowFocusOwner = new WeakReference<>(recentFocusOwner);

    maximizeIfNeeded(owner);

    // the actual title will be taken from a sheet panel, not from this dialog
    myWindow = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
    myWindow.getRootPane().putClientProperty("apple.awt.draggableWindowBackground", Boolean.FALSE);
    myWindow.setUndecorated(true);
    myWindow.setBackground(Gray.TRANSPARENT);
    myController = new SheetController(this, title, message, icon, buttons, defaultButton, doNotAskOption, focusedButton);
    Disposer.register(this, myController);

    imageHeight = 0;
    ComponentListener componentAdapter = new ComponentAdapter() {
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
    myWindow.setSize(myController.SHEET_NC_WIDTH, 0);
    myWindow.setOpacity(0.0f);

    ComponentAdapter componentListener = new ComponentAdapter() {
      @Override
      public void componentShown(@NotNull ComponentEvent e) {
        super.componentShown(e);
        myWindow.setOpacity(1.0f);
        myWindow.setSize(myController.SHEET_NC_WIDTH, myController.SHEET_NC_HEIGHT);
      }
    };
    myWindow.addComponentListener(componentListener);
    Disposer.register(this, () -> myWindow.removeComponentListener(componentListener));

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

        boolean modifiersAreNotPressed = (modifiers & modifiersUnion) == 0;

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
    _showTouchBar();
    myWindow.setVisible(true);
    LaterInvocator.leaveModal(myWindow);

    Component focusCandidate = beforeShowFocusOwner.get();

    if (focusCandidate == null) {
      focusCandidate = getGlobalInstance().getLastFocusedFor(getGlobalInstance().getLastFocusedIdeWindow());
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

  private void _showTouchBar() {
    if (!TouchBarsManager.isTouchBarEnabled()) {
      return;
    }

    final Disposable tb = TouchBarsManager.showDialogWrapperButtons(myController.getSheetPanel());
    if (tb != null) {
      Disposer.register(this, tb);
    }
  }

  private static void maximizeIfNeeded(@Nullable Window owner) {
    if (owner instanceof Frame) {
      Frame f = (Frame)owner;
      if (f.getState() == Frame.ICONIFIED) {
        f.setState(Frame.NORMAL);
      }
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
        imageHeight = enlarge ? (int)((float)myController.SHEET_NC_HEIGHT * percentage) :
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
