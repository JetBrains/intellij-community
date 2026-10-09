// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.sceneBuilder;

import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

/**
 * @author Alexander Lobas
 */
public interface SceneBuilder {
  @RequiresEdt
  JComponent getPanel();

  @RequiresEdt
  void reload(@NotNull Runnable onLibraryChanged);

  @RequiresEdt
  void close();
}