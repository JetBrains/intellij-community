// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi;

import com.intellij.psi.NavigatablePsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface EditorConfigSection extends NavigatablePsiElement {

  @NotNull
  EditorConfigHeader getHeader();

  @NotNull
  List<EditorConfigOption> getOptionList();

  boolean containsKey(@NotNull EditorConfigFlatOptionKey key);

}
