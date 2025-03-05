// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi;

import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement;
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface EditorConfigOptionValuePair extends EditorConfigDescribableElement {

  @NotNull
  List<EditorConfigOptionValueIdentifier> getOptionValueIdentifierList();

  @NotNull
  List<EditorConfigOptionValueList> getOptionValueListList();

  @NotNull EditorConfigDescribableElement getFirst();

  @NotNull EditorConfigDescribableElement getSecond();

  @Override
  @Nullable EditorConfigDescriptor getDescriptor(boolean smart);

}
