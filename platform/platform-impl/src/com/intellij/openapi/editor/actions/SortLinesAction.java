// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.actionSystem.EditorAction;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class SortLinesAction extends EditorAction {
  public SortLinesAction() {
    super(new AbstractPermuteLinesHandler() {
      @Override
      public void permute(String @NotNull [] lines) {
        Arrays.parallelSort(lines);
      }
    });
  }
}
