package org.jetbrains.plugins.javaFX.sceneBuilder;// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import javax.swing.*;

/**
 * @author Alexander Lobas
 */
public interface SceneBuilder {
  JComponent getPanel();

  boolean reload();

  void close();
}