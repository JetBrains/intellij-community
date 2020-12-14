// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.gradle.dsl.parser.groovy;

import com.android.tools.idea.gradle.dsl.parser.GradleDslConverterFactory;
import com.android.tools.idea.gradle.dsl.parser.GradleDslParser;
import com.android.tools.idea.gradle.dsl.parser.GradleDslWriter;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

public class GroovyDslConverterFactory implements GradleDslConverterFactory {
  @Override
  public boolean canConvert(PsiFile psiFile) {
    return psiFile instanceof GroovyFile;
  }

  @Override
  public GradleDslWriter createWriter() {
    return new GroovyDslWriter();
  }

  @Override
  public GradleDslParser createParser(PsiFile psiFile, GradleDslFile gradleDslFile) {
    return new GroovyDslParser((GroovyFile)psiFile, gradleDslFile);
  }
}
