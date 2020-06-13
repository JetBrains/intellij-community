// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Delegates all changes to {@link #textChanged(DocumentEvent)}.
 */
public abstract class DocumentAdapter implements DocumentListener {
  @Override
  public void insertUpdate(@NotNull DocumentEvent e) {
    textChanged(e);
  }

  @Override
  public void removeUpdate(@NotNull DocumentEvent e) {
    textChanged(e);
  }

  @Override
  public void changedUpdate(@NotNull DocumentEvent e) {
    textChanged(e);
  }

  protected abstract void textChanged(@NotNull DocumentEvent e);
}
