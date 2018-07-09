// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.search.MethodTextOccurrenceProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrLiteralMethodSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> {
  @Override
  public void processQuery(@NotNull MethodReferencesSearch.SearchParameters p, @NotNull Processor<? super PsiReference> consumer) {
    final PsiMethod method = p.getMethod();
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) return;

    final String name = method.getName();
    if (StringUtil.isEmpty(name)) return;

    final boolean strictSignatureSearch = p.isStrictSignatureSearch();
    final PsiMethod[] methods = strictSignatureSearch ? new PsiMethod[]{method} : aClass.findMethodsByName(name, false);

    SearchScope accessScope = GroovyScopeUtil.getEffectiveScope(methods);
    final SearchScope restrictedByAccess = GroovyScopeUtil.restrictScopeToGroovyFiles(p.getEffectiveSearchScope(), accessScope);

    final String textToSearch = findLongestWord(name);

    p.getOptimizer().searchWord(textToSearch, restrictedByAccess, UsageSearchContext.IN_STRINGS, true, method,
                                new MethodTextOccurrenceProcessor(aClass, strictSignatureSearch, methods));
  }

  @NotNull
  private static String findLongestWord(@NotNull String sequence) {
    final List<String> words = StringUtil.getWordsIn(sequence);
    if (words.isEmpty()) return sequence;

    String longest = words.get(0);
    for (String word : words) {
      if (word.length() > longest.length()) longest = word;
    }

    return longest;
  }

  public GrLiteralMethodSearcher() {
    super(true);
  }
}
