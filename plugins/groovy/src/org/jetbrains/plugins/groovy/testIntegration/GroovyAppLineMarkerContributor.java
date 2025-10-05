// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.testIntegration;

import com.intellij.execution.application.ApplicationRunLineMarkerProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.MainMethodSearcherBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.util.GroovyMainMethodSearcher;

import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.METHOD_IDENTIFIERS;

final class GroovyAppLineMarkerContributor extends ApplicationRunLineMarkerProvider {
  @Override
  protected boolean isIdentifier(@NotNull PsiElement e) {
    return METHOD_IDENTIFIERS.contains(e.getNode().getElementType());
  }

  @Override
  protected MainMethodSearcherBase getMainMethodUtil() {
    return GroovyMainMethodSearcher.INSTANCE;
  }
}
