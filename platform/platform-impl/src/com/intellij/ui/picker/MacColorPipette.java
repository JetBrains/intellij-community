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
package com.intellij.ui.picker;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.ColorPicker;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.FoundationLibrary;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.BitUtil;
import com.intellij.util.ui.UIUtil;
import com.sun.jna.Native;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;

public class MacColorPipette extends ColorPipetteBase {
  private static final Logger LOG = Logger.getInstance(MacColorPipette.class);
  private static final int PIXELS = 17;
  private static final int ZOOM = 10;
  private static final int SIZE = PIXELS * ZOOM;
  private static final int DIALOG_SIZE = SIZE + 20;

  @SuppressWarnings("UseJBColor") private final Color myTransparentColor = new Color(0, 0, 0, 1);

  public MacColorPipette(@NotNull ColorPicker picker, @NotNull ColorListener listener) {
    super(picker, listener);
  }

  @NotNull
  @Override
  protected Dialog getOrCreatePickerDialog() {
    Dialog pickerDialog = getPickerDialog();
    if (pickerDialog == null) {
      pickerDialog = super.getOrCreatePickerDialog();
      pickerDialog.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent event) {
          super.keyPressed(event);
          int diff = BitUtil.isSet(event.getModifiers(), Event.SHIFT_MASK) ? 10 : 1;
          Point location = updateLocation();
          if (myRobot != null && location != null) {
            switch (event.getKeyCode()) {
              case KeyEvent.VK_DOWN:
                myRobot.mouseMove(location.x, location.y + diff);
                break;
              case KeyEvent.VK_UP:
                myRobot.mouseMove(location.x, location.y - diff);
                break;
              case KeyEvent.VK_LEFT:
                myRobot.mouseMove(location.x - diff, location.y);
                break;
              case KeyEvent.VK_RIGHT:
                myRobot.mouseMove(location.x + diff, location.y);
                break;
            }
            updateLocation();
          }
        }
      });
      final JLabel label = new JLabel() {
        @Override
        public void paint(Graphics g) {
          applyRenderingHints(g);

          Dialog pickerDialog = getPickerDialog();
          if (pickerDialog != null && pickerDialog.isShowing()) {
            Point mouseLoc = updateLocation();
            if (mouseLoc == null) return;

            //final int pixels = UIUtil.isRetina(graphics2d) ? PIXELS / 2 + 1 : PIXELS;
            int left = PIXELS / 2 + 1;
            Rectangle captureRectangle = new Rectangle(mouseLoc.x - left, mouseLoc.y - left, PIXELS, PIXELS);
            BufferedImage captureScreen = captureScreen(pickerDialog, captureRectangle);
            if (captureScreen == null || captureScreen.getWidth() < PIXELS || captureRectangle.getHeight() < PIXELS) {
              cancelPipette();
              return;
            }

            //noinspection UseJBColor
            Color newColor = new Color(captureScreen.getRGB(captureRectangle.width / 2, captureRectangle.height / 2));

            Graphics2D graphics2d = ((Graphics2D)g);
            Point offset = new Point(10, 10);
            graphics2d.setComposite(AlphaComposite.Clear);
            graphics2d.fillRect(0, 0, getWidth(), getHeight());

            graphics2d.setComposite(AlphaComposite.Src);
            graphics2d.clip(new Ellipse2D.Double(offset.x, offset.y, SIZE, SIZE));
            graphics2d.drawImage(captureScreen, offset.x, offset.y, SIZE, SIZE, this);

            // paint magnifier
            graphics2d.setComposite(AlphaComposite.SrcOver);
            drawPixelGrid(graphics2d, offset);
            drawCenterPixel(graphics2d, offset, newColor);
            drawCurrentColorRectangle(graphics2d, offset, newColor);
            graphics2d.setClip(0, 0, getWidth(), getHeight());
            drawMagnifierBorder(newColor, graphics2d, offset);

            pickerDialog.repaint();
            if (!newColor.equals(getColor())) {
              setColor(newColor);
              notifyListener(newColor, 300);
            }
          }
        }
      };
      pickerDialog.add(label);
      pickerDialog.setSize(DIALOG_SIZE, DIALOG_SIZE);
      pickerDialog.setBackground(myTransparentColor);
      
      BufferedImage emptyImage = UIUtil.createImage(1, 1, Transparency.TRANSLUCENT);
      pickerDialog.setCursor(myParent.getToolkit().createCustomCursor(emptyImage, new Point(0, 0), "ColorPicker"));
    }
    return pickerDialog;
  }

  private static void applyRenderingHints(@NotNull Graphics graphics) {
    UIUtil.applyRenderingHints(graphics);
    if (graphics instanceof Graphics2D) {
      ((Graphics2D)graphics).setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
      ((Graphics2D)graphics).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      ((Graphics2D)graphics).setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }
  }

  private static void drawCurrentColorRectangle(@NotNull Graphics2D graphics, @NotNull Point offset, @NotNull Color currentColor) {
    graphics.setColor(Gray._0.withAlpha(150));
    int x = SIZE / 4 + offset.x;
    int y = SIZE * 3 / 4 + offset.y;
    int width = SIZE / 2;
    int height = SIZE / 8;
    graphics.fillRoundRect(x, y, width, height, 10, 10);
    
    graphics.setColor(Gray._255);
    String colorString = currentColor.getRed() + " " + currentColor.getGreen() + " " + currentColor.getBlue();
    FontMetrics metrics = graphics.getFontMetrics();
    int stringWidth = metrics.stringWidth(colorString);
    int stringHeight = metrics.getHeight();
    graphics.drawString(colorString, x + (width - stringWidth) / 2, y + stringHeight);
  }

  private static void drawCenterPixel(@NotNull Graphics2D graphics, @NotNull Point offset, @NotNull Color currentColor) {
    graphics.setColor(ColorUtil.isDark(currentColor) ? Gray._255.withAlpha(150) : Gray._0.withAlpha(150));
    graphics.drawRect((SIZE - ZOOM) / 2 + offset.x, (SIZE - ZOOM) / 2 + offset.y, ZOOM, ZOOM);
  }

  private static void drawPixelGrid(@NotNull Graphics2D graphics, @NotNull Point offset) {
    graphics.setColor(Gray._0.withAlpha(10));
    for (int i = 0; i < PIXELS; i++) {
      int cellOffset = i * ZOOM;
      graphics.drawLine(cellOffset + offset.x, offset.y, cellOffset + offset.x, SIZE + offset.y);
      graphics.drawLine(offset.x, cellOffset + offset.y, SIZE + offset.x, cellOffset + offset.y);
    }
  }

  private static void drawMagnifierBorder(@NotNull Color currentColor, @NotNull Graphics2D graphics, @NotNull Point offset) {
    graphics.setColor(currentColor.darker());
    graphics.setStroke(new BasicStroke(5));
    graphics.draw(new Ellipse2D.Double(offset.x, offset.y, SIZE, SIZE));
  }

  @Override
  public boolean isAvailable() {
    return captureScreen(null, new Rectangle(0, 0, 1, 1)) != null;
  }

  @Nullable
  private static BufferedImage captureScreen(@Nullable Window belowWindow, @NotNull Rectangle rect) {
    ID pool = Foundation.invoke("NSAutoreleasePool", "new");
    try {
      ID windowId = belowWindow != null ? MacUtil.findWindowFromJavaWindow(belowWindow) : null;
      Foundation.NSRect nsRect = new Foundation.NSRect(rect.x, rect.y, rect.width, rect.height);
      ID cgWindowId = windowId != null ? Foundation.invoke(windowId, "windowNumber") : ID.NIL;
      int windowListOptions = cgWindowId != null
                              ? FoundationLibrary.kCGWindowListOptionOnScreenBelowWindow
                              : FoundationLibrary.kCGWindowListOptionAll;
      int windowImageOptions = FoundationLibrary.kCGWindowImageNominalResolution;
      ID cgImageRef = Foundation.cgWindowListCreateImage(nsRect, windowListOptions, cgWindowId, windowImageOptions);

      ID bitmapRep = Foundation.invoke(Foundation.invoke("NSBitmapImageRep", "alloc"), "initWithCGImage:", cgImageRef);
      ID nsImage = Foundation.invoke(Foundation.invoke("NSImage", "alloc"), "init");
      Foundation.invoke(nsImage, "addRepresentation:", bitmapRep);
      ID data = Foundation.invoke(nsImage, "TIFFRepresentation");
      ID bytes = Foundation.invoke(data, "bytes");
      ID length = Foundation.invoke(data, "length");
      ByteBuffer byteBuffer = Native.getDirectByteBuffer(bytes.longValue(), length.longValue());
      Foundation.invoke(nsImage, "release");
      byte[] b = new byte[byteBuffer.remaining()];
      byteBuffer.get(b);
      return ImageIO.read(new ByteArrayInputStream(b));
    }
    catch (Throwable t) {
      LOG.error(t);
      return null;
    }
    finally {
      Foundation.invoke(pool, "release");
    }
  }
}
