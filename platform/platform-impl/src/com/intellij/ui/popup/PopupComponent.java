// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup;

import com.intellij.openapi.diagnostic.Logger;

import java.awt.*;

public interface PopupComponent {
  Logger LOG = Logger.getInstance(PopupComponent.class);

  void hide(boolean dispose);

  void show();

  Window getWindow();

  void setRequestFocus(boolean requestFocus);
}

