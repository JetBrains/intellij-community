// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import org.editorconfig.language.psi.interfaces.EditorConfigHeaderElement;

public interface EditorConfigHeader extends EditorConfigHeaderElement {

  @NotNull
  List<EditorConfigPattern> getPatternList();

  @NotNull
  List<EditorConfigPatternEnumeration> getPatternEnumerationList();

  boolean isValidGlob();

}
