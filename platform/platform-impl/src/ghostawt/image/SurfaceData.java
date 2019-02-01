// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt.image;

import sun.java2d.loops.SurfaceType;

import java.awt.*;

public class SurfaceData {


  // Looks like we need to transfer here Canvas info from browser.
  public int getDefaultScale() {return 1;}

  // Canvas area from browser.
  public Rectangle getBounds() {
    return new Rectangle(0,0,0,0);
  }

  public Boolean isValid() {
    return false;
  }

  public void markDirty() {

  }

  public SurfaceType getSurfaceType() {
    return SurfaceType.IntRgbx;
  }

  public int getTransparency() {
    return Transparency.OPAQUE;
  }

}
