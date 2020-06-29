// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.wm.WelcomeScreen;

import javax.swing.*;
import java.awt.*;

public abstract class AbstractWelcomeScreen extends JPanel implements WelcomeScreen, DataProvider {
  protected AbstractWelcomeScreen() {
    super(new BorderLayout());
  }

  @Override
  public void dispose() {
  }
}
