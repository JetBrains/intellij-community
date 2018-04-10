// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.sceneBuilder;

/**
 * @author Alexander Lobas
 */
public interface EditorCallback {
  void saveChanges(String content);

  void handleError(Throwable e);
}