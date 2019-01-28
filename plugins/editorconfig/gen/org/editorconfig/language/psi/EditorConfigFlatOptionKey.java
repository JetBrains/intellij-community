// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi;

import org.editorconfig.language.psi.interfaces.EditorConfigIdentifierElement;
import org.editorconfig.language.psi.reference.EditorConfigFlatOptionKeyReference;
import org.jetbrains.annotations.NotNull;

public interface EditorConfigFlatOptionKey extends EditorConfigIdentifierElement {

  boolean definesSameOption(@NotNull EditorConfigFlatOptionKey element);

  @Override
  @NotNull
  EditorConfigFlatOptionKeyReference getReference();
}
