// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.config;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public abstract class TaskRepositoryEditor implements Disposable {

  public abstract JComponent createComponent();

  public @Nullable JComponent getPreferredFocusedComponent() {
    return null;
  }

  @Override
  public void dispose() {}
}
