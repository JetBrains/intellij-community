// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.extensions;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public final class GroovyMapCompletionUtil {
  public static void addKeyVariants(@NotNull GroovyMapContentProvider contentProvider, @NotNull GrExpression qualifier, @Nullable PsiElement resolve, @NotNull CompletionResultSet result) {
    for (String key : GroovyMapContentProvider.getKeyVariants(contentProvider, qualifier, resolve)) {
      LookupElement lookup = LookupElementBuilder.create(key);
      lookup = PrioritizedLookupElement.withPriority(lookup, 1);
      result.addElement(lookup);
    }
  }
}
