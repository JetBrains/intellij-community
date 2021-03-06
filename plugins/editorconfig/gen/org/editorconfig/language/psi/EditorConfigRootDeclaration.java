// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.NavigatablePsiElement;

public interface EditorConfigRootDeclaration extends NavigatablePsiElement {

  @NotNull
  EditorConfigRootDeclarationKey getRootDeclarationKey();

  @NotNull
  List<EditorConfigRootDeclarationValue> getRootDeclarationValueList();

  boolean isValidRootDeclaration();

}
