// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement;
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor;

public interface EditorConfigOptionValuePair extends EditorConfigDescribableElement {

  @NotNull
  List<EditorConfigOptionValueIdentifier> getOptionValueIdentifierList();

  @NotNull
  List<EditorConfigOptionValueList> getOptionValueListList();

  @NotNull EditorConfigDescribableElement getFirst();

  @NotNull EditorConfigDescribableElement getSecond();

  @Nullable EditorConfigDescriptor getDescriptor(boolean smart);

}
