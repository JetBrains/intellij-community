// This is a generated file. Not intended for manual editing.
package com.intellij.editorconfig.common.syntax.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface EditorConfigOptionValuePair extends EditorConfigDescribableElement {

  @NotNull
  List<EditorConfigOptionValueIdentifier> getOptionValueIdentifierList();

  @NotNull
  List<EditorConfigOptionValueList> getOptionValueListList();

  @NotNull EditorConfigDescribableElement getFirst();

  @NotNull EditorConfigDescribableElement getSecond();

}
