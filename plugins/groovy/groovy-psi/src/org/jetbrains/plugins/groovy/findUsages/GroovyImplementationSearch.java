// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.codeInsight.navigation.MethodImplementationsSearch;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

/**
 * @author Maxim.Medvedev
 */
public class GroovyImplementationSearch implements QueryExecutor<PsiElement, DefinitionsScopedSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final DefinitionsScopedSearch.SearchParameters queryParameters, @NotNull final Processor<? super PsiElement> consumer) {
    return ReadAction.compute(() -> {
      final PsiElement source = queryParameters.getElement();
      if (!source.isValid()) return true;

      if (source instanceof GrAccessorMethod) {
        GrField property = ((GrAccessorMethod)source).getProperty();
        return consumer.process(property);
      }
      else {
        final SearchScope searchScope = queryParameters.getScope();
        if (source instanceof GrMethod) {
          GrReflectedMethod[] reflectedMethods = ((GrMethod)source).getReflectedMethods();
          for (GrReflectedMethod reflectedMethod : reflectedMethods) {
            if (!MethodImplementationsSearch.processImplementations(reflectedMethod, consumer, searchScope)) return false;
          }
        }

        else if (source instanceof GrField) {
          for (GrAccessorMethod method : GroovyPropertyUtils.getFieldAccessors((GrField)source)) {
            if (!MethodImplementationsSearch.processImplementations(method, consumer, searchScope)) return false;
          }
        }
      }
      return true;
    });
  }
}
