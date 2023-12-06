// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface EditorConfigCharClassPattern extends EditorConfigPattern {

  @Nullable
  EditorConfigCharClassExclamation getCharClassExclamation();

  @NotNull
  List<EditorConfigCharClassLetter> getCharClassLetterList();

}
