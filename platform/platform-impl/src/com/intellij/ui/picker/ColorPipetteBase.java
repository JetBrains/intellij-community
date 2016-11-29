/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui.picker;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public abstract class ColorPipetteBase implements ColorPipette {
  private final Alarm myColorListenersNotifier = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
  protected final JComponent myParent;
  private final ColorListener myColorListener;
  protected final Robot myRobot;
  private JDialog myPickerFrame;

  private Color myCurrentColor;
  private Color myInitialColor;

  public ColorPipetteBase(@NotNull JComponent parent, @NotNull ColorListener colorListener) {
    myParent = parent;
    myColorListener = colorListener;
    myRobot = createRobot();
  }

  public static boolean canUseMacPipette() {
    return SystemInfo.isMac && Registry.is("ide.mac.new.color.picker");
  }

  @Override
  public void pickAndClose() {
    PointerInfo pointerInfo = MouseInfo.getPointerInfo();
    Color pixelColor = getPixelColor(pointerInfo.getLocation());
    cancelPipette();
    notifyListener(pixelColor, 0);
    setInitialColor(pixelColor);
  }

  protected Color getPixelColor(Point location) {
    if (SystemInfo.isMac) {
      BufferedImage image = MacColorPipette.captureScreen(myPickerFrame, new Rectangle(location.x, location.y, 1, 1));
      if (image != null) {
        //noinspection UseJBColor
        return new Color(image.getRGB(0, 0));
      }
    }
    return myRobot.getPixelColor(location.x, location.y);
  }

  @Nullable
  protected Color getInitialColor() {
    return myInitialColor;
  }

  @Override
  public void setInitialColor(@Nullable Color initialColor) {
    myInitialColor = initialColor;
    setColor(initialColor);
  }

  protected void setColor(@Nullable Color color) {
    myCurrentColor = color;
  }

  @Nullable
  @Override
  public Color getColor() {
    return myCurrentColor;
  }

  @Override
  public Dialog show() {
    Dialog picker = getOrCreatePickerDialog();
    updateLocation();
    picker.setVisible(true);
    return picker;
  }

  @Nullable
  protected Point updateLocation() {
    PointerInfo pointerInfo = MouseInfo.getPointerInfo();
    if (pointerInfo == null) return null;

    Point mouseLocation = pointerInfo.getLocation();
    Dialog pickerDialog = getPickerDialog();
    if (pickerDialog != null && mouseLocation != null) {
      pickerDialog.setLocation(mouseLocation.x - pickerDialog.getWidth() / 2, mouseLocation.y - pickerDialog.getHeight() / 2);
    }
    return mouseLocation;
  }

  @Nullable
  protected Dialog getPickerDialog() {
    return myPickerFrame;
  }

  @NotNull
  protected Dialog getOrCreatePickerDialog() {
    if (myPickerFrame == null) {
      Window owner = SwingUtilities.getWindowAncestor(myParent);
      if (owner instanceof Dialog) {
        myPickerFrame = new JDialog((Dialog)owner);
      }
      else if (owner instanceof Frame) {
        myPickerFrame = new JDialog((Frame)owner);
      }
      else {
        myPickerFrame = new JDialog(new JFrame());
      }
      myPickerFrame.setTitle("intellijPickerDialog");
    }
    myPickerFrame.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        e.consume();
        pickAndClose();
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        e.consume();
      }
    });
    myPickerFrame.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
          case KeyEvent.VK_ESCAPE:
            cancelPipette();
            break;
          case KeyEvent.VK_ENTER:
            pickAndClose();
            break;
        }
      }
    });

    myPickerFrame.setUndecorated(true);
    myPickerFrame.setAlwaysOnTop(!SystemInfo.isJavaVersionAtLeast("1.8.0") || canUseMacPipette());

    JRootPane rootPane = myPickerFrame.getRootPane();
    rootPane.putClientProperty("Window.shadow", Boolean.FALSE);
    return myPickerFrame;
  }

  protected void notifyListener(@NotNull final Color c, int delayMillis) {
    if (!myColorListenersNotifier.isDisposed()) {
      myColorListenersNotifier.cancelAllRequests();
      myColorListenersNotifier.addRequest(() -> myColorListener.colorChanged(c, this), delayMillis);
    }
  }

  @Override
  public boolean imageUpdate(Image image, int i, int i1, int i2, int i3, int i4) {
    return false;
  }

  @Override
  public void cancelPipette() {
    Dialog pickerDialog = getPickerDialog();
    if (pickerDialog != null) {
      pickerDialog.setVisible(false);
    }
    Color initialColor = getInitialColor();
    if (initialColor != null) {
      notifyListener(initialColor, 0);
    }
  }

  @Override
  public void dispose() {
    UIUtil.dispose(myPickerFrame);
    myPickerFrame = null;
    setInitialColor(null);
    setColor(null);
  }
  
  @Nullable
  private static Robot createRobot() {
    try {
      return new Robot();
    }
    catch (AWTException e) {
      return null;
    }
  }
}
