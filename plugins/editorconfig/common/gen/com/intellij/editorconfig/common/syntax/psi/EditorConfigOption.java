// This is a generated file. Not intended for manual editing.
package com.intellij.editorconfig.common.syntax.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

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

  @NotNull List<@NotNull String> getKeyParts();

  @Nullable EditorConfigDescribableElement getAnyValue();

}
