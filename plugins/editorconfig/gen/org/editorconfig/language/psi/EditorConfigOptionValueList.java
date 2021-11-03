// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement;
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor;

public interface EditorConfigOptionValueList extends EditorConfigDescribableElement {

  @NotNull
  List<EditorConfigOptionValueIdentifier> getOptionValueIdentifierList();

  @Nullable EditorConfigDescriptor getDescriptor(boolean smart);

}
