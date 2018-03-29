// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.*;

abstract class TBItem {
  final String myUid;
  protected ID myNativePeer = ID.NIL; // java wrapper holds native object

  TBItem(@NotNull String uid) { myUid = uid; }

  ID getNativePeer() {
    if (myNativePeer == ID.NIL)
      myNativePeer = _createNativePeer();

    return myNativePeer;
  }
  final void updateNativePeer() {
    if (myNativePeer == ID.NIL)
      return;
    _updateNativePeer();
  }
  final void releaseNativePeer() {
    if (myNativePeer == ID.NIL)
      return;
    _releaseChildBars();
    Foundation.invoke(myNativePeer, "release");
    myNativePeer = ID.NIL;
  }

  protected abstract void _updateNativePeer();  // called from EDT
  protected abstract ID _createNativePeer();    // called from AppKit

  protected void _releaseChildBars() {}         // called from EDT

  static byte[] getRaster(Icon icon) {
    if (icon == null)
      return null;

    final int w = icon.getIconWidth();
    final int h = icon.getIconHeight();
    final WritableRaster
      raster = Raster.createInterleavedRaster(new DataBufferByte(w * h * 4), w, h, 4 * w, 4, new int[]{0, 1, 2, 3}, (Point) null);
    final ColorModel
      colorModel = new ComponentColorModel(ColorModel.getRGBdefault().getColorSpace(), true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);
    final BufferedImage image = new BufferedImage(colorModel, raster, false, null);
    final Graphics2D g = image.createGraphics();
    g.setComposite(AlphaComposite.SrcOver);
    icon.paintIcon(null, g, 0, 0);
    g.dispose();

    return ((DataBufferByte)image.getRaster().getDataBuffer()).getData();
  }

  static int getIconW(Icon icon) {
    return icon == null ? 0 : icon.getIconWidth();
  }
  static int getIconH(Icon icon) {
    return icon == null ? 0 : icon.getIconHeight();
  }
}
