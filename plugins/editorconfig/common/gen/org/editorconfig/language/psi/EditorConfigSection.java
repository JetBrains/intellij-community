// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.NavigatablePsiElement;

public interface EditorConfigSection extends NavigatablePsiElement {

  @NotNull
  EditorConfigHeader getHeader();

  @NotNull
  List<EditorConfigOption> getOptionList();

}
