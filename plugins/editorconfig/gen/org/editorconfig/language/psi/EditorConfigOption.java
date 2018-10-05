// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi;

import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement;
import org.editorconfig.language.schema.descriptors.impl.EditorConfigOptionDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface EditorConfigOption extends EditorConfigDescribableElement {

  @Nullable
  EditorConfigFlatOptionKey getFlatOptionKey();

  @Nullable
  EditorConfigOptionValueIdentifier getOptionValueIdentifier();

  @Nullable
  EditorConfigOptionValueList getOptionValueList();

  @Nullable
  EditorConfigOptionValuePair getOptionValuePair();

  @Nullable
  EditorConfigQualifiedOptionKey getQualifiedOptionKey();

  @NotNull
  List<String> getKeyParts();

  @Nullable
  EditorConfigDescribableElement getAnyValue();

  @Override
  @Nullable
  EditorConfigOptionDescriptor getDescriptor(boolean smart);
}
