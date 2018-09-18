// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import javax.swing.*;
import java.awt.*;

public class StubItem extends JMenuItem {
  public StubItem(){
    setEnabled(false);
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension();
  }
}
