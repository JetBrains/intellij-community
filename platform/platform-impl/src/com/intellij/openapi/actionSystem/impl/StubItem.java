// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl;

import javax.swing.*;
import java.awt.*;

public final class StubItem extends JMenuItem {
  public StubItem(){
    setEnabled(false);
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension();
  }
}
