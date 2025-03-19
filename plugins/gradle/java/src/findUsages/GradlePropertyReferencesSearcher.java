// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.findUsages;

import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Kirill Marchuk
 */
public final class GradlePropertyReferencesSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {

  private static final String[] GRADLE_PROPERTY_FILES = {"gradle.properties"};
  private static final String GRADLE_DSL_EXTENSION = "gradle";

  public GradlePropertyReferencesSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull ReferencesSearch.SearchParameters queryParameters, @NotNull Processor<? super PsiReference> consumer) {
    final PsiElement element = queryParameters.getElementToSearch();
    if (element instanceof Property property
        && isContainingFileGradlePropertyFile(element.getContainingFile())
        && queryParameters.getEffectiveSearchScope() instanceof GlobalSearchScope
    ) {

      if (property.getName() == null) {
        return;
      }

      SearchScope effectiveSearchScope = queryParameters.getEffectiveSearchScope();
      if (SearchScope.isEmptyScope(effectiveSearchScope)) {
        return;
      }

      final GlobalSearchScope gradleSearchScope =
        new FileByExtensionSearchScope((GlobalSearchScope)effectiveSearchScope, GRADLE_DSL_EXTENSION);
      final short searchContext = (short)(UsageSearchContext.IN_CODE | UsageSearchContext.IN_STRINGS);
      final MyProcessor processor = new MyProcessor(property);

      queryParameters.getOptimizer().searchWord(
        property.getName(),
        gradleSearchScope,
        searchContext,
        false,
        property,
        processor);
    }
  }

  private static boolean isContainingFileGradlePropertyFile(PsiFile file) {
    for (String filename : GRADLE_PROPERTY_FILES) {
      if (file.getName().equalsIgnoreCase(filename)) {
        return true;
      }
    }
    return false;
  }

  private static class MyProcessor extends RequestResultProcessor {
    final PsiElement target;

    MyProcessor(PsiElement target) {
      super(target);
      this.target = target;
    }

    @Override
    public boolean processTextOccurrence(@NotNull PsiElement element,
                                         int offsetInElement,
                                         @NotNull Processor<? super PsiReference> consumer) {
      if (element instanceof PsiReference) {
        if (!consumer.process((PsiReference)element)) {
          return false;
        }
      }
      return true;
    }
  }

  private static class FileByExtensionSearchScope extends DelegatingGlobalSearchScope {
    private final @NotNull String extension;

    FileByExtensionSearchScope(GlobalSearchScope scope,
                               @NotNull String extension) {
      super(scope);
      this.extension = extension;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return super.contains(file) && extension.equalsIgnoreCase(file.getExtension());
    }
  }
}
