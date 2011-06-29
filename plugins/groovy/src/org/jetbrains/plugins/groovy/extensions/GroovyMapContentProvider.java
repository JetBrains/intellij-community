package org.jetbrains.plugins.groovy.extensions;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.Collection;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public abstract class GroovyMapContentProvider {

  public static final ExtensionPointName<GroovyMapContentProvider> EP_NAME = ExtensionPointName.create("org.intellij.groovy.mapContentProvider");

  public void addKeyVariants(@NotNull GrExpression qualifier, @Nullable PsiElement resolve, @NotNull CompletionResultSet result) {
    for (String key : getKeyVariants(qualifier, resolve)) {
      LookupElement lookup = LookupElementBuilder.create(key);
      lookup = PrioritizedLookupElement.withPriority(lookup, 1);
      result.addElement(lookup);
    }
  }

  protected Collection<String> getKeyVariants(@NotNull GrExpression qualifier, @Nullable PsiElement resolve) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public PsiType getValueType(@NotNull GrExpression qualifier, @Nullable PsiElement resolve, @NotNull String key) {
    return null;
  }

}
