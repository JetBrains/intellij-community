// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.actionSystem.EditorAction;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

final class UniqueLinesAction extends EditorAction {
  UniqueLinesAction() {
    super(new AbstractPermuteLinesHandler() {
      @Override
      public void permute(String @NotNull [] lines) {
        var set = new HashSet<String>();
        for (int i = 0; i < lines.length; i++) {
          if (!set.add(lines[i])) lines[i] = null;
        }
      }
    });
  }
}
