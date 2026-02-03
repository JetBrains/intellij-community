// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate;

import com.intellij.xdebugger.impl.ui.XDebuggerEditorBase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class EvaluationInputComponent {
  private final String myTitle;

  protected EvaluationInputComponent(String title) {
    myTitle = title;
  }

  public String getTitle() {
    return myTitle;
  }

  public abstract @NotNull XDebuggerEditorBase getInputEditor();

  public abstract JPanel getMainComponent();

  public abstract void addComponent(JPanel contentPanel, JPanel resultPanel);
}
