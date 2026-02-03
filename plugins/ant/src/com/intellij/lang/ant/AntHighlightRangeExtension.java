// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant;

import com.intellij.codeInsight.daemon.impl.HighlightRangeExtension;
import com.intellij.psi.PsiFile;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;


public final class AntHighlightRangeExtension implements HighlightRangeExtension {

  @Override
  public boolean isForceHighlightParents(final @NotNull PsiFile psiFile) {
    return XmlUtil.isAntFile(psiFile);
  }
}
