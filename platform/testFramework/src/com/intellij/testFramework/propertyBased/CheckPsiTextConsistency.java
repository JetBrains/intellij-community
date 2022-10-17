// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.propertyBased;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

public class CheckPsiTextConsistency extends ActionOnFile {

  public CheckPsiTextConsistency(@NotNull PsiFile file) {
    super(file);
  }

  @Override
  public void performCommand(@NotNull Environment env) {
    env.logMessage(toString());
    PsiTestUtil.checkFileStructure(getFile());
  }
}
