// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.actionSystem.EditorAction;
import org.jetbrains.annotations.NotNull;

public class ReverseLinesAction extends EditorAction {
  public ReverseLinesAction() {
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
