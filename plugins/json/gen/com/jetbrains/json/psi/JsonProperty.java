// This is a generated file. Not intended for manual editing.
package com.jetbrains.json.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JsonProperty extends PsiElement {

  @NotNull
  JsonPropertyName getPropertyName();

  @Nullable
  JsonValue getValue();

  @NotNull
  String getName();

}
