// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.ID;
import com.sun.jna.Callback;
import com.sun.jna.Library;

import javax.swing.*;
import java.awt.*;
import java.awt.image.*;

public interface NSTLibrary extends Library {
  ID createTouchBar(String name);
  void releaseTouchBar(ID tbObj);

  ID registerSpacing(ID tbObj, String type); // allowed types: 'small', 'large', 'flexible'
  ID registerButtonText(ID tbObj, String text, Action action);
  ID registerButtonImg(ID tbObj, byte[] raster4ByteRGBA, int w, int h, Action action);
  ID registerPopover(ID tbObj, String text, byte[] raster4ByteRGBA, int w, int h, int popW);
  void setPopoverExpandTouchBar(ID popoverObj, ID expandTbObj);
  void setPopoverTapAndHoldTouchBar(ID popoverObj, ID tapHoldTbObj);

  void selectAllItemsToShow(ID tbObj);
  void setTouchBar(ID tbObj);

  ID registerScrubber(ID tbObj, int scrubW);
  void addScrubberItem(ID scrubObj, String text, byte[] raster4ByteRGBA, int w, int h, Action action);

  interface Action extends Callback {
    void execute();
  }

  static byte[] getRasterFromIcon(Icon icon) {
    final int w = icon.getIconWidth();
    final int h = icon.getIconHeight();
    final WritableRaster raster = Raster.createInterleavedRaster(new DataBufferByte(w * h * 4), w, h, 4 * w, 4, new int[]{0, 1, 2, 3}, (Point) null);
    final ColorModel colorModel = new ComponentColorModel(ColorModel.getRGBdefault().getColorSpace(), true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);
    final BufferedImage image = new BufferedImage(colorModel, raster, false, null);
    final Graphics2D g = image.createGraphics();
    g.setComposite(AlphaComposite.SrcOver);
    icon.paintIcon(null, g, 0, 0);
    g.dispose();

    return ((DataBufferByte)image.getRaster().getDataBuffer()).getData();
  }
}