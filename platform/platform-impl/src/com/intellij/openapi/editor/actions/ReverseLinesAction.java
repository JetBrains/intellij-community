// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.actionSystem.EditorAction;
import org.jetbrains.annotations.NotNull;

final class ReverseLinesAction extends EditorAction {
  ReverseLinesAction() {
    super(new AbstractPermuteLinesHandler() {
      @Override
      public void permute(String @NotNull [] lines) {
        int halfSize = lines.length / 2;
        for (int i = 0; i < halfSize; i++) {
          int oppositeI = lines.length - 1 - i;
          String tmp = lines[i];
          lines[i] = lines[oppositeI];
          lines[oppositeI] = tmp;
        }
      }
    });
  }
}
