// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.refactoring.JavaFxPropertyElement;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxMethodSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final ReferencesSearch.SearchParameters queryParameters,
                         @NotNull final Processor<? super PsiReference> consumer) {
    final PsiElement elementToSearch = queryParameters.getElementToSearch();
    if (elementToSearch instanceof PsiMethod) {
      searchMethod((PsiMethod)elementToSearch, queryParameters, consumer);
    }
    if (elementToSearch instanceof JavaFxPropertyElement) {
      final JavaFxPropertyElement propertyElement = (JavaFxPropertyElement)elementToSearch;
      final JavaFxPropertyReference propertyReference = propertyElement.getPropertyReference();
      final PsiMethod staticSetter = propertyReference.getStaticSetter();
      if (staticSetter != null) {
        searchMethod(staticSetter, queryParameters, consumer);
      }
    }
    return true;
  }

  private static void searchMethod(@NotNull PsiMethod psiMethod, @NotNull ReferencesSearch.SearchParameters queryParameters,
                                   @NotNull Processor<? super PsiReference> consumer) {
    final Project project = PsiUtilCore.getProjectInReadAction(psiMethod);
    final SearchScope scope =
      ReadAction.compute(queryParameters::getEffectiveSearchScope);
    if (scope instanceof LocalSearchScope) {
      final VirtualFile[] vFiles = ((LocalSearchScope)scope).getVirtualFiles();
      for (VirtualFile vFile : vFiles) {
        if (JavaFxFileTypeFactory.isFxml(vFile)) {
          final PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);
          if (psiFile != null) {
            final Boolean goOn = ReadAction.compute(() -> searchMethodInFile(psiMethod, psiFile, consumer));
            if (!goOn) break;
          }
        }
      }
    }
    else if (scope instanceof GlobalSearchScope) {
      final String propertyName = ReadAction.compute(() -> PropertyUtilBase.getPropertyName(psiMethod.getName()));
      if (propertyName == null) return;

      final String className = ReadAction.compute(() -> {
          final PsiClass psiClass = psiMethod.getContainingClass();
          return psiClass != null ? psiClass.getName() : null;
        });
      if (className == null) return;

      final GlobalSearchScope fxmlScope = new JavaFxScopeEnlarger.GlobalFxmlSearchScope((GlobalSearchScope)scope);
      final VirtualFile[] filteredFiles = ReadAction.compute(() ->
        CacheManager.SERVICE.getInstance(project).getVirtualFilesWithWord(className, UsageSearchContext.IN_PLAIN_TEXT, fxmlScope, true));
      if (ArrayUtil.isEmpty(filteredFiles)) return;

      final GlobalSearchScope filteredScope = GlobalSearchScope.filesScope(project, ContainerUtil.newHashSet(filteredFiles));
      ReadAction.run(() -> CacheManager.SERVICE.getInstance(project).processFilesWithWord(
        file -> searchMethodInFile(psiMethod, file, consumer), propertyName, UsageSearchContext.IN_PLAIN_TEXT, filteredScope, true));
    }
  }

  private static boolean searchMethodInFile(@NotNull PsiMethod psiMethod,
                                            @NotNull PsiFile file,
                                            @NotNull Processor<? super PsiReference> consumer) {
    final Ref<Boolean> stopped = new Ref<>(false);
    file.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlElement(XmlElement element) {
        if (stopped.get()) return;
        super.visitXmlElement(element);
      }

      @Override
      public void visitXmlAttribute(XmlAttribute attribute) {
        if (stopped.get()) return;
        final PsiReference[] references = attribute.getReferences();
        for (PsiReference reference : references) {
          if ((reference instanceof JavaFxStaticPropertyReference || reference instanceof JavaFxEventHandlerReference) &&
              reference.isReferenceTo(psiMethod)) {
            if (!consumer.process(reference)) {
              stopped.set(true);
              return;
            }
          }
        }
      }
    });
    return !stopped.get();
  }
}
