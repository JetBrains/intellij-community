// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup;

import com.intellij.openapi.util.registry.Registry;

import javax.swing.Popup;
import java.awt.Component;
import java.awt.GraphicsEnvironment;

public final class OurHeavyWeightPopup extends Popup {
  public OurHeavyWeightPopup(Component owner, Component content, int x, int y) {
    super(owner, content, x, y);
  }

  public static boolean isEnabled() {
    return !GraphicsEnvironment.isHeadless() && Registry.is("our.heavy.weight.popup");
  }
}
