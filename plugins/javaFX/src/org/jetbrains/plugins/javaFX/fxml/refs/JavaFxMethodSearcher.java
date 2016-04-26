/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.indexing.JavaFxImportsIndex;
import org.jetbrains.plugins.javaFX.refactoring.JavaFxStaticPropertyElement;

import java.util.List;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxMethodSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final ReferencesSearch.SearchParameters queryParameters,
                         @NotNull final Processor<PsiReference> consumer) {
    final PsiElement elementToSearch = queryParameters.getElementToSearch();
    if (elementToSearch instanceof PsiMethod) {
      searchMethod((PsiMethod)elementToSearch, queryParameters, consumer);
    }
    if (elementToSearch instanceof JavaFxStaticPropertyElement) {
      searchMethod(((JavaFxStaticPropertyElement)elementToSearch).getMethod(), queryParameters, consumer);
    }
    return true;
  }

  private static void searchMethod(@NotNull PsiMethod psiMethod, @NotNull ReferencesSearch.SearchParameters queryParameters,
                                   @NotNull Processor<PsiReference> consumer) {
    final PsiClass containingClass = ApplicationManager.getApplication().runReadAction((Computable<PsiClass>)psiMethod::getContainingClass);
    if (containingClass != null) {
      final String qualifiedName = ApplicationManager.getApplication().runReadAction((Computable<String>)containingClass::getQualifiedName);
      if (qualifiedName != null) {
        final Project project = PsiUtilCore.getProjectInReadAction(containingClass);
        final List<PsiFile> withImport = JavaFxImportsIndex.findFxmlWithImport(project, qualifiedName);
        for (final PsiFile file : withImport) {
          ApplicationManager.getApplication().runReadAction(() -> searchMethodInFile(psiMethod, file, queryParameters, consumer));
        }
      }
    }
  }

  private static void searchMethodInFile(@NotNull PsiMethod psiMethod,
                                         @NotNull PsiFile file,
                                         @NotNull ReferencesSearch.SearchParameters queryParameters,
                                         @NotNull Processor<PsiReference> consumer) {
    final VirtualFile virtualFile = file.getViewProvider().getVirtualFile();
    final SearchScope searchScope = queryParameters.getEffectiveSearchScope();
    boolean contains = searchScope instanceof LocalSearchScope ? ((LocalSearchScope)searchScope).isInScope(virtualFile) :
                       ((GlobalSearchScope)searchScope).contains(virtualFile);
    if (contains) {
      file.accept(new XmlRecursiveElementVisitor() {
        @Override
        public void visitXmlAttribute(XmlAttribute attribute) {
          final PsiReference[] references = attribute.getReferences();
          for (PsiReference reference : references) {
            if ((reference instanceof JavaFxStaticPropertyReference || reference instanceof JavaFxEventHandlerReference) &&
                reference.isReferenceTo(psiMethod)) {
              consumer.process(reference);
            }
          }
        }
      });
    }
  }
}
