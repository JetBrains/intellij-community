/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.internal.colorpicker;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * User: Vassiliy.Kudryashov
 */
public class ColorPickerAction extends ToggleAction implements DumbAware {
  private ColorPicker myColorPicker = null;
  private Point myInitialLocation = null;

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myColorPicker != null;
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    if (myColorPicker != null) {
      myInitialLocation = myColorPicker.getLocation();
      myColorPicker.dispose();
      myColorPicker = null;
    }
    if (state) {
      myColorPicker = new ColorPicker(myInitialLocation);
    }
  }

  private static class ColorPicker extends JDialog implements ActionListener, MouseListener, MouseMotionListener {
    final GrayFilter myFilter = new GrayFilter(true, 50);
    final Color[] myPrev = new Color[]{Color.BLACK};
    final JLabel myLabel = new JLabel(" 255,255,255 ");
    private boolean myColoring = true;
    private boolean myMultiline = false;
    private Point myStartPoint;
    private final Timer myTimer;

    public ColorPicker(Point initialLocation) throws HeadlessException {
      super((Frame)null, "ColorPicker", false);
      myLabel.setFont(new Font("Monospaced", Font.BOLD, 13));
      myLabel.setOpaque(true);
      myLabel.addMouseListener(this);
      myLabel.addMouseMotionListener(this);
      setUndecorated(true);
      getContentPane().add(myLabel);
      pack();

      if (initialLocation != null) {
        setLocation(initialLocation);
        checkBounds(this);
      }
      else {
        setDefaultBounds(this);
      }

      myLabel.setText("");
      setAlwaysOnTop(true);
      setVisible(true);


      myTimer = new Timer(100, this);
      myTimer.start();
    }

    @Override
    public void dispose() {
      if (myTimer != null && myTimer.isRunning()) {
        myTimer.stop();
      }
      super.dispose();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      try {
        PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        if (pointerInfo == null) return;
        GraphicsDevice device = pointerInfo.getDevice();
        Point location = pointerInfo.getLocation();
        if (device == null || location == null || getBounds().contains(location)) return;
        GraphicsConfiguration defaultConfiguration = device.getDefaultConfiguration();
        if (defaultConfiguration == null) return;
        Rectangle rectangle = defaultConfiguration.getBounds();
        location.x -= rectangle.x;
        location.y -= rectangle.y;
        Robot robot = new Robot(device);
        Color color = robot.getPixelColor(location.x, location.y);

        if (myPrev[0].equals(color)) return;

        myPrev[0] = color;
        int gray = (myFilter.filterRGB(0, 0, color.getRGB()) & 0xff);
        updateLabel(color);
        if (myColoring) {
          myLabel.setBackground(color);
          myLabel.setForeground(gray > 150 ? Color.BLACK : Color.WHITE);
        }
        else {
          myLabel.setBackground(Color.WHITE);
          myLabel.setForeground(Color.BLACK);
        }
        checkBounds(this);
      }
      catch (AWTException e1) {
      }
    }

    private static String getString(int i) {
      if (i < 10) {
        return "  " + i;
      }
      if (i < 100) {
        return " " + i;
      }
      return String.valueOf(i);
    }

    private void updateLabel(Color color) {
      if (color == null) {
        return;
      }
      if (!myMultiline) {
        myLabel.setText(" " + getString(color.getRed()) + "," + getString(color.getGreen()) + "," + getString(color.getBlue())+" ");
      }
      else {
        myLabel.setText("<html>&nbsp;" +
                        getString(color.getRed()) +
                        "&nbsp;<br>&nbsp;" +
                        getString(color.getGreen()) +
                        "&nbsp;<br>&nbsp;" +
                        getString(color.getBlue()));
      }
    }

    private void doPack() {
      updateLabel(Color.WHITE);
      pack();
      checkBounds(this);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (e.isControlDown()) {
        myMultiline = !myMultiline;
        doPack();
        return;
      }
      myColoring = !myColoring;
    }

    @Override
    public void mousePressed(MouseEvent e) {
      myStartPoint = e.getPoint();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      myStartPoint = null;
      checkBounds(this);
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      if (myStartPoint == null) {
        return;
      }
      Point shift = e.getPoint();
      shift.x -= myStartPoint.x;
      shift.y -= myStartPoint.y;
      Point location = getLocation();
      location.x += shift.x;
      location.y += shift.y;
      setLocation(location);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }

    private static void checkBounds(Window window) {
      Rectangle bounds = window.getBounds();
      GraphicsDevice[] screenDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
      int maxSquare = 0;
      Rectangle inter = null;
      for (GraphicsDevice screenDevice : screenDevices) {
        Rectangle candidate = getPatchedBounds(screenDevice.getDefaultConfiguration()).intersection(bounds);
        if (candidate.isEmpty()) continue;
        if (candidate.equals(bounds)) return;
        if (candidate.width * candidate.height > maxSquare) {
          maxSquare = candidate.width * candidate.height;
          inter = candidate;
        }
      }
      if (inter != null) {
        bounds.translate(
          2 * (inter.x + inter.width / 2 - (bounds.x + bounds.width / 2)),
          2 * (inter.y + inter.height / 2 - (bounds.y + bounds.height / 2)));
        window.setBounds(bounds);
      }
      else {
        setDefaultBounds(window);
      }
    }

    private static void setDefaultBounds(Window window) {
      Rectangle bounds = getPatchedBounds(
        GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration());
      window.setLocation(bounds.x + (bounds.width - window.getWidth()) / 2, bounds.y);
    }

    private static Rectangle getPatchedBounds(GraphicsConfiguration graphicsConfiguration) {
      Rectangle bounds = graphicsConfiguration.getBounds();
      Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration);
      bounds.x += insets.left;
      bounds.y += insets.top;
      bounds.width -= (insets.left + insets.right);
      bounds.height -= (insets.top + insets.bottom);
      return bounds;
    }
  }
}
