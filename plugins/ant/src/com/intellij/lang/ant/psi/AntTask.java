package com.intellij.lang.ant.psi;

import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.Nullable;

public interface AntTask extends AntStructuredElement, PsiNamedElement {

  @Nullable
  String getId();

}
