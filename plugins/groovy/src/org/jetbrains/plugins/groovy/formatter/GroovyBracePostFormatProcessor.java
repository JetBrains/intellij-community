// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.formatter;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

final class GroovyBracePostFormatProcessor implements PostFormatProcessor {
  @NotNull
  @Override
  public PsiElement processElement(@NotNull PsiElement source, @NotNull CodeStyleSettings settings) {
    if (source instanceof GroovyPsiElement) {
      return new GroovyBraceEnforcer(settings).process(((GroovyPsiElement)source));
    }
    else {
      return source;
    }
  }

  @NotNull
  @Override
  public TextRange processText(@NotNull PsiFile source, @NotNull TextRange rangeToReformat, @NotNull CodeStyleSettings settings) {
    if (source instanceof GroovyFile) {
      return new GroovyBraceEnforcer(settings).processText(((GroovyFile)source), rangeToReformat);
    }
    else {
      return rangeToReformat;
    }
  }
}
