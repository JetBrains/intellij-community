// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.testIntegration;

import com.intellij.execution.application.ApplicationRunLineMarkerProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.METHOD_IDENTIFIERS;

final class GroovyAppLineMarkerContributor extends ApplicationRunLineMarkerProvider {
  @Override
  protected boolean isIdentifier(@NotNull PsiElement e) {
    return METHOD_IDENTIFIERS.contains(e.getNode().getElementType());
  }
}
