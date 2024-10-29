// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import javax.swing.*;
import java.awt.*;

/**
 * A heavyweight facade for {@link JPanel}.
 *
 * @see HwFacadeHelper
 * @author tav
 */
public class HwFacadeJPanel extends JPanel {
  private final HwFacadeHelper myHwFacadeHelper = HwFacadeHelper.create(this);

  @Override
  public void addNotify() {
    super.addNotify();
    myHwFacadeHelper.addNotify();
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    myHwFacadeHelper.removeNotify();
  }

  @SuppressWarnings("deprecation")
  @Override
  public void show() {
    super.show();
    myHwFacadeHelper.show();
  }

  @SuppressWarnings("deprecation")
  @Override
  public void hide() {
    super.hide();
    myHwFacadeHelper.hide();
  }

  @Override
  public void paint(Graphics g) {
    myHwFacadeHelper.paint(g, (gg) -> super.paint(gg));
  }
}