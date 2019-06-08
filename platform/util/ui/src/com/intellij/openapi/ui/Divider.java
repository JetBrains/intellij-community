// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class Divider extends JPanel {
  public Divider(LayoutManager layout) {
    super(layout);
  }

  public abstract void setResizeEnabled(boolean resizeEnabled);

  public abstract void setSwitchOrientationEnabled(boolean switchOrientationEnabled);

  public abstract void setOrientation(boolean vertical);
}
