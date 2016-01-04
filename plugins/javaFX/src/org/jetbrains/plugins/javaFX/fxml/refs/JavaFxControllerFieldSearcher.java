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
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.indexing.JavaFxControllerClassIndex;

import java.util.List;

/**
 * User: anna
 * Date: 3/29/13
 */
public class JavaFxControllerFieldSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters>{
  @Override
  public boolean execute(@NotNull final ReferencesSearch.SearchParameters queryParameters, @NotNull final Processor<PsiReference> consumer) {
    final PsiElement elementToSearch = queryParameters.getElementToSearch();
    if (elementToSearch instanceof PsiField) {
      final PsiField field = (PsiField)elementToSearch;
      final PsiClass containingClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
        @Override
        public PsiClass compute() {
          return field.getContainingClass();
        }
      });
      if (containingClass != null) {
        final String qualifiedName = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
          @Override
          public String compute() {
            return containingClass.getQualifiedName(); 
          }
        });
        if (qualifiedName != null) {
          Project project = PsiUtilCore.getProjectInReadAction(containingClass);
          final List<PsiFile> fxmlWithController =
            JavaFxControllerClassIndex.findFxmlWithController(project, qualifiedName);
          for (final PsiFile file : fxmlWithController) {
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              @Override
              public void run() {
                final String fieldName = field.getName();
                final VirtualFile virtualFile = file.getViewProvider().getVirtualFile();
                final SearchScope searchScope = queryParameters.getEffectiveSearchScope();
                boolean contains = searchScope instanceof LocalSearchScope ? ((LocalSearchScope)searchScope).isInScope(virtualFile) :
                                   ((GlobalSearchScope)searchScope).contains(virtualFile);
                if (contains) {
                  file.accept(new XmlRecursiveElementVisitor() {
                    @Override
                    public void visitXmlAttributeValue(final XmlAttributeValue value) {
                      final PsiReference reference = value.getReference();
                      if (reference != null) {
                        final PsiElement resolve = reference.resolve();
                        if (resolve instanceof XmlAttributeValue) {
                          final PsiElement parent = resolve.getParent();
                          if (parent instanceof XmlAttribute) {
                            final XmlAttribute attribute = (XmlAttribute)parent;
                            if (FxmlConstants.FX_ID.equals(attribute.getName()) && fieldName.equals(attribute.getValue())) {
                              consumer.process(reference);
                            }
                          }
                        }
                      }
                    }
                  });
                }
              }
            });
          }
        }
      }
    }
    return true;
  }
}
