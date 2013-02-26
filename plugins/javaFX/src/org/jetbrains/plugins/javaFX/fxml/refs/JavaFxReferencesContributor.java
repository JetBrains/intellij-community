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

import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonClassNames;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.patterns.PsiJavaPatterns.literalExpression;

/**
 * User: anna
 * Date: 2/22/13
 */
public class JavaFxReferencesContributor extends PsiReferenceContributor {

  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(literalExpression().and(new FilterPattern(new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        final PsiLiteralExpression literalExpression = (PsiLiteralExpression)context;
        PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(literalExpression, PsiMethodCallExpression.class);
        if (callExpression != null && "getResource".equals(callExpression.getMethodExpression().getReferenceName())) {
          final PsiMethodCallExpression superCall = PsiTreeUtil.getParentOfType(callExpression, PsiMethodCallExpression.class, true);
          if (superCall != null) {
            final PsiReferenceExpression methodExpression = superCall.getMethodExpression();
            if ("load".equals(methodExpression.getReferenceName())) {
              final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
              PsiClass psiClass = null;
              if (qualifierExpression instanceof PsiReferenceExpression) {
                final PsiElement resolve = ((PsiReferenceExpression)qualifierExpression).resolve();
                if (resolve instanceof PsiClass) {
                  psiClass = (PsiClass)resolve;
                }
              } else if (qualifierExpression != null) {
                psiClass = PsiUtil.resolveClassInType(qualifierExpression.getType());
              }
              if (psiClass != null && JavaFxCommonClassNames.JAVAFX_FXML_FXMLLOADER.equals(psiClass.getQualifiedName())) {
                return true;
              }
            }
          }
        }
        return false;
      }

      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    })), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull final PsiElement element, @NotNull ProcessingContext context) {
        final Object value = ((PsiLiteralExpression)element).getValue();
        final PsiDirectory directory = element.getContainingFile().getParent();
        if (!(value instanceof String) || directory == null) return PsiReference.EMPTY_ARRAY;
        final VirtualFileSystem fs = directory.getVirtualFile().getFileSystem();
        return new FileReferenceSet((String)value, element, 1, null, ((NewVirtualFileSystem)fs).isCaseSensitive()) {
          @NotNull
          @Override
          public Collection<PsiFileSystemItem> getDefaultContexts() {
            if (!directory.isValid()) {
              return super.getDefaultContexts();
            }
            return Collections.<PsiFileSystemItem>singletonList(directory);
          }
        }.getAllReferences();
      }
    });
  }
}
