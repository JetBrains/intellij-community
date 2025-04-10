// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement;
import org.editorconfig.language.schema.descriptors.impl.EditorConfigOptionDescriptor;

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

  @Nullable EditorConfigOptionDescriptor getDescriptor(boolean smart);

}
