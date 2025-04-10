// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import org.editorconfig.language.psi.interfaces.EditorConfigIdentifierElement;
import org.editorconfig.language.psi.reference.EditorConfigFlatOptionKeyReference;

public interface EditorConfigFlatOptionKey extends EditorConfigIdentifierElement {

  boolean definesSameOption(@NotNull EditorConfigFlatOptionKey element);

  @NotNull EditorConfigFlatOptionKeyReference getReference();

}
