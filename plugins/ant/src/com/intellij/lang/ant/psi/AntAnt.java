package com.intellij.lang.ant.psi;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

public interface AntAnt extends AntTask {

  @Nullable
  PsiFile getCalledAntFile();
}
