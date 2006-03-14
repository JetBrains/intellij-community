package com.intellij.lang.ant.psi;

import com.intellij.psi.PsiFile;

public interface AntFile extends AntElement, PsiFile {
  AntProject getAntProject();
}
