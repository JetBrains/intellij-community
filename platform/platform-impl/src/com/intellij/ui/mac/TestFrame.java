// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac;

import com.intellij.jdkEx.JdkEx;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.JBDimension;
import org.jetbrains.annotations.NotNull;
import sun.awt.ModalExclude;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class TestFrame {
  public static void main(String[] args) {
    createAndShowFrame();
  }

  static int myCount;
  static List<JFrame> myFrames = new ArrayList<>();
  static Window myPopup;

  private static void createAndShowFrame() {
    JFrame frame = createFrame();

    myFrames.add(frame);

    JPanel buttons = new JPanel();

    JButton addButton = new JButton("+");
    addButton.addActionListener(e -> createAndShowFrame());
    addButton.addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        if (e.getKeyChar() == 'w') {
          frame.dispose();
          myFrames.get(myFrames.size() - 2).dispose();
        }
      }
    });
    buttons.add(addButton);

    JEditorPane editorPane = new JEditorPane();
    editorPane.setEditable(false);
    editorPane.setFocusable(false);

    JButton infoButton = new JButton("Info");
    infoButton.addActionListener(e -> {
      StringBuilder builder = new StringBuilder();
      GraphicsDevice device = frame.getGraphicsConfiguration().getDevice();
      builder.append("Device: " + device.getDisplayMode() + "\n" +
                     "Bounds: " + frame.getGraphicsConfiguration().getBounds() + "\n" +
                     "Menu: " + frame.getJMenuBar());
      editorPane.setText(builder.toString());
    });
    buttons.add(infoButton);

    JButton clearButton = new JButton("Clear Info");
    clearButton.addActionListener(e -> editorPane.setText(null));
    buttons.add(clearButton);

    JButton noExitButton = new JButton("No Exit");
    noExitButton.addActionListener(e -> frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE));
    buttons.add(noExitButton);

    JButton popup = new JButton("Show Popup");
    popup.addActionListener(e -> myPopup = showPopupWindow(frame));
    buttons.add(popup);

    JButton hidePopup = new JButton("Hide Popup");
    hidePopup.addActionListener(e -> {
      if (myPopup != null) {
        myPopup.dispose();
        myPopup = null;
      }
    });
    buttons.add(hidePopup);

    JPanel filler = new NonOpaquePanel();
    filler.setPreferredSize(new JBDimension(-1, 50));

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(filler, BorderLayout.NORTH);
    panel.add(buttons);
    panel.add(editorPane, BorderLayout.SOUTH);
    frame.setContentPane(panel);

    frame.getRootPane().setOpaque(false);
    //LafManagerImpl.fixPopupWeight();
    JPopupMenu menu = new JBPopupMenu()/* {
      @Override
      public boolean isOpaque() {
        return false;
      }

      @Override
      public void paint(Graphics g) {
        out.println("--------------------");
        out.println("Border: " + getBorder() + " | " + getUI() + " | " + this);
        Container parent = getParent();
        while (parent != null) {
          if (parent instanceof JComponent) {
            JComponent c = (JComponent)parent;
            out.println("Border: " + c.getBorder() + " | " + c.getUI() + " | " + c);
          }
          else {
            out.println(parent);
          }
          parent = parent.getParent();
        }
      }
    }*/;
    menu.putClientProperty("apple.awt.windowCornerRadius", Float.valueOf(10));
    menu.setOpaque(false);
    menu.add("Foo");
    menu.add("Bar");
    menu.add("AAAAAAAAA");
    menu.add("BBBBBBB");
    menu.add("CCCCCCCCCCCCC");
    panel.setComponentPopupMenu(menu);

    /*JTextField field = new JTextField("10");
    filler.add(field, BorderLayout.NORTH);
    field.addActionListener(e -> {
      menu.putClientProperty("Radius", Float.valueOf(field.getText()));
    });*/

    frame.setBounds(100, 100, 400, 300);
    frame.setVisible(true);
  }

  @NotNull
  private static JFrame createFrame() {
    JFrame frame = new JFrame("Window: " + myCount++) {
      @Override
      public void addNotify() {
        super.addNotify();
        Foundation.executeOnMainThread(true, false, () -> {
          ID window = MacUtil.getWindowFromJavaWindow(this);
          Foundation.invoke(window, "setTabbingIdentifier:", Foundation.nsString("Test-AwtWindow-WithTabs"));
        });
      }
    };
    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    JdkEx.setTabbingMode(frame, null);
    Foundation.invoke("NSWindow", "setAllowsAutomaticWindowTabbing:", true);
    return frame;
  }

  private static Window showPopupWindow(Window parent) {
    MyPopupWindow window = new MyPopupWindow(parent);
    window.setContentPane(new JPanel() {
      {
        setOpaque(true);
        setBackground(Color.green);
      }
      @Override
      public Dimension getPreferredSize() {
        return new JBDimension(200, 200);
      }
    });
    window.setBounds(100, 100, 100, 200);
    window.show();
    return window;
  }

  private static class MyPopupWindow extends JWindow implements ModalExclude {
    public MyPopupWindow(Window parent) {
      super(parent);
      setFocusableWindowState(false);
      setType(Window.Type.POPUP);

      // Popups are typically transient and most likely won't benefit
      // from true double buffering.  Turn it off here.
      JRootPane pane = getRootPane();
      try {
        ReflectionUtil.getDeclaredMethod(pane.getClass(), "setUseTrueDoubleBuffering", boolean.class).invoke(pane, Boolean.FALSE);
      }
      catch (Exception e) {
        e.printStackTrace();
      }
      rootPane.putClientProperty("Window.alpha", 1.0f);
      rootPane.putClientProperty("Window.shadow", null);
      rootPane.putClientProperty("apple.awt.windowCornerRadius", Float.valueOf(10));
      // Try to set "always-on-top" for the popup window.
      // Applets usually don't have sufficient permissions to do it.
      // In this case simply ignore the exception.
      try {
        setAlwaysOnTop(true);
      } catch (SecurityException se) {
        // setAlwaysOnTop is restricted,
        // the exception is ignored
      }
    }

    public void update(Graphics g) {
      paint(g);
    }

    @SuppressWarnings("deprecation")
    public void show() {
      this.pack();
      if (getWidth() > 0 && getHeight() > 0) {
        super.show();
      }
    }
  }
}