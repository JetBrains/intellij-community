// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import org.editorconfig.language.psi.interfaces.EditorConfigHeaderElement;

public interface EditorConfigPattern extends EditorConfigHeaderElement {

  @NotNull
  List<EditorConfigAsteriskPattern> getAsteriskPatternList();

  @NotNull
  List<EditorConfigCharClass> getCharClassList();

  @NotNull
  List<EditorConfigDoubleAsteriskPattern> getDoubleAsteriskPatternList();

  @NotNull
  List<EditorConfigFlatPattern> getFlatPatternList();

  @NotNull
  List<EditorConfigQuestionPattern> getQuestionPatternList();

}
