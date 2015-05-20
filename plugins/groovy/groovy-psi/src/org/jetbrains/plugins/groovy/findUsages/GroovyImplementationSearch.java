/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.codeInsight.navigation.MethodImplementationsSearch;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
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
  public boolean execute(@NotNull final DefinitionsScopedSearch.SearchParameters queryParameters, @NotNull final Processor<PsiElement> consumer) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
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
      }
    });
  }
}
