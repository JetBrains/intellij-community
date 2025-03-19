// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.actionSystem.EditorAction;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

final class SortLinesAction extends EditorAction {
  SortLinesAction() {
    super(new AbstractPermuteLinesHandler() {
      @Override
      public void permute(String @NotNull [] lines) {
        Arrays.parallelSort(lines);
      }
    });
  }
}
