// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi;

import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement;
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface EditorConfigOptionValueList extends EditorConfigDescribableElement {

  @NotNull
  List<EditorConfigOptionValueIdentifier> getOptionValueIdentifierList();

  @Override
  @Nullable
  EditorConfigDescriptor getDescriptor(boolean smart);
}
