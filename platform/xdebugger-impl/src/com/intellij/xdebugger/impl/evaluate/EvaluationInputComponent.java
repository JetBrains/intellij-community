package com.intellij.xdebugger.impl.evaluate;

import com.intellij.xdebugger.impl.ui.XDebuggerEditorBase;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class EvaluationInputComponent {
  private final String myTitle;

  protected EvaluationInputComponent(String title) {
    myTitle = title;
  }

  public String getTitle() {
    return myTitle;
  }

  protected abstract XDebuggerEditorBase getInputEditor();

  public abstract void addComponent(JPanel contentPanel, JPanel resultPanel);
}
