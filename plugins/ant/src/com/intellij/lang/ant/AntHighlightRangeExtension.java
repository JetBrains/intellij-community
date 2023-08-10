// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant;

import com.intellij.codeInsight.daemon.impl.HighlightRangeExtension;
import com.intellij.psi.PsiFile;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;


public class AntHighlightRangeExtension implements HighlightRangeExtension {

  @Override
  public boolean isForceHighlightParents(@NotNull final PsiFile file) {
    return XmlUtil.isAntFile(file);
  }
}
