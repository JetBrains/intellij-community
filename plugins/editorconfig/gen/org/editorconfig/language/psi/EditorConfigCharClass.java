// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import org.editorconfig.language.psi.interfaces.EditorConfigHeaderElement;

public interface EditorConfigCharClass extends EditorConfigHeaderElement {

  @Nullable
  EditorConfigCharClassExclamation getCharClassExclamation();

  @NotNull
  List<EditorConfigCharClassLetter> getCharClassLetterList();

}
