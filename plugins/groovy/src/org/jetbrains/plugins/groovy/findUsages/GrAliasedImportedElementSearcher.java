/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Maxim.Medvedev
 */
public class GrAliasedImportedElementSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {

  public GrAliasedImportedElementSearcher() {
    super(true);
  }

  @Override
  public void processQuery(ReferencesSearch.SearchParameters parameters, Processor<PsiReference> consumer) {
    final PsiElement target = parameters.getElementToSearch();
    if (!(target instanceof PsiMember) || !(target instanceof PsiNamedElement)) return;

    final String name = ((PsiNamedElement)target).getName();
    if (name == null) return;

    final SearchScope onlyGroovy = PsiUtil.restrictScopeToGroovyFiles(parameters.getEffectiveSearchScope());

    if (target instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)target;
      if (GroovyPropertyUtils.isSimplePropertyAccessor(method)) {
        final PsiField field = GroovyPropertyUtils.findFieldForAccessor(method, true);
        if (field != null) {
          final String propertyName = field.getName();
          if (propertyName != null) {
            final MyProcessor processor = new MyProcessor(method, GroovyPropertyUtils.getAccessorPrefix(method));
            parameters.getOptimizer().searchWord(propertyName, onlyGroovy, UsageSearchContext.IN_CODE, true, processor);
          }
        }
      }
    }

    parameters.getOptimizer().searchWord(name, onlyGroovy, UsageSearchContext.IN_CODE, true, new MyProcessor(target, null));

  }

  private static class MyProcessor extends RequestResultProcessor {
    private final PsiElement myTarget;
    private final String prefix;

    MyProcessor(PsiElement target, @Nullable String prefix) {
      myTarget = target;
      this.prefix = prefix;
    }

    @Override
    public boolean processTextOccurrence(final PsiElement element, int offsetInElement, Processor<PsiReference> consumer) {
      String alias = getAlias(element);
      if (alias == null) return true;

      final PsiReference reference = element.getReference();
      if (reference == null) {
        return true;
      }
      if (!reference.isReferenceTo(myTarget instanceof GrAccessorMethod ? ((GrAccessorMethod)myTarget).getProperty() : myTarget)) {
        return true;
      }

      final SearchRequestCollector collector = new SearchRequestCollector();
      final SearchScope fileScope = new LocalSearchScope(element.getContainingFile());
      collector.searchWord(alias, fileScope, UsageSearchContext.IN_CODE, true, myTarget);
      if (prefix != null) {
        collector.searchWord(prefix + GroovyPropertyUtils.capitalize(alias), fileScope, UsageSearchContext.IN_CODE, true, myTarget);
      }


      return element.getManager().getSearchHelper().processRequests(collector, consumer);
    }

    @Nullable
    private static String getAlias(final PsiElement element) {
      if (!(element.getParent() instanceof GrImportStatement)) return null;
      final GrImportStatement importStatement = (GrImportStatement)element.getParent();
      if (!importStatement.isAliasedImport()) return null;
      return importStatement.getImportedName();
    }

  }

}
