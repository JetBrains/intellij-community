// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
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

public class JavaFxControllerFieldSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters>{
  @Override
  public boolean execute(@NotNull final ReferencesSearch.SearchParameters queryParameters, @NotNull final Processor<? super PsiReference> consumer) {
    final PsiElement elementToSearch = queryParameters.getElementToSearch();
    if (elementToSearch instanceof PsiField) {
      final PsiField field = (PsiField)elementToSearch;
      final PsiClass containingClass = ReadAction.compute(() -> field.getContainingClass());
      if (containingClass != null) {
        final String qualifiedName = ReadAction.compute(() -> containingClass.getQualifiedName());
        if (qualifiedName != null) {
          Project project = PsiUtilCore.getProjectInReadAction(containingClass);
          final List<PsiFile> fxmlWithController =
            JavaFxControllerClassIndex.findFxmlWithController(project, qualifiedName);
          for (final PsiFile file : fxmlWithController) {
            ApplicationManager.getApplication().runReadAction(() -> {
              final String fieldName = field.getName();
              if (fieldName == null) return;
              final VirtualFile virtualFile = file.getViewProvider().getVirtualFile();
              final SearchScope searchScope = queryParameters.getEffectiveSearchScope();
              if (searchScope.contains(virtualFile)) {
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
            });
          }
        }
      }
    }
    return true;
  }
}
