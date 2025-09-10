// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;


/**
 * Introduced within IDEA-183815 Capability to add a content between code lines in the editor
 */
final class MarginPositions {
  private final float[] x;
  private final int[] y;

  MarginPositions(int size) {
    x = new float[size];
    y = new int[size];
  }

  float[] x() {
    return x;
  }

  int[] y() {
    return y;
  }
}
