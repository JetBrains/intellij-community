/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.style;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

/**
 * @author Maxim.Medvedev
 */
public class ImportStaticIntention extends Intention {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.intentions.style.ImportStaticIntention");
  private static final Key<PsiElement> TEMP_REFERENT_USER_DATA = new Key<PsiElement>("TEMP_REFERENT_USER_DATA");

  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    LOG.assertTrue(element instanceof GrReferenceExpression);
    final GrReferenceExpression ref = (GrReferenceExpression)element;
    final PsiElement resolved = ref.resolve();
    LOG.assertTrue(resolved instanceof PsiMember);

    final PsiClass containingClass = ((PsiMember)resolved).getContainingClass();
    LOG.assertTrue(containingClass != null);
    final String qname = containingClass.getQualifiedName();
    final String name = ((PsiMember)resolved).getName();

    final PsiFile containingFile = element.getContainingFile();
    LOG.assertTrue(containingFile instanceof GroovyFile);
    ((GroovyFile)containingFile).accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(GrReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (name.equals(expression.getReferenceName())) {
          PsiElement resolved = expression.resolve();
          if (resolved != null) {
            expression.putUserData(TEMP_REFERENT_USER_DATA, resolved);
          }
        }
      }
    });

    final GrImportStatement importStatement =
      GroovyPsiElementFactory.getInstance(project).createImportStatementFromText(qname + "." + name, true, false, null);


    ((GroovyFile)containingFile).addImport(importStatement);

    for (PsiReference reference : ReferencesSearch.search(resolved, new LocalSearchScope(containingFile))) {
      final PsiElement refElement = reference.getElement();
      if (refElement instanceof GrQualifiedReference) {
        GrReferenceAdjuster.shortenReference((GrQualifiedReference)refElement);
      }
    }


    ((GroovyFile)containingFile).accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(GrReferenceExpression expression) {
        super.visitReferenceExpression(expression);

        if (expression.getTypeArgumentList() != null &&
            expression.getTypeArgumentList().getFirstChild() != null) {
          expression.putUserData(TEMP_REFERENT_USER_DATA, null);

          return;
        }

        if (name.equals(expression.getReferenceName())) {
          if (expression.isQualified()) {
            if (expression.getQualifierExpression() instanceof GrReferenceExpression) {
              PsiElement aClass = ((GrReferenceExpression)expression.getQualifierExpression()).resolve();
              if (aClass == ((PsiMember)resolved).getContainingClass()) {
                GrReferenceAdjuster.shortenReference(expression);
              }
            }
          }
          else {
            PsiElement referent = expression.getUserData(TEMP_REFERENT_USER_DATA);
            if (referent instanceof PsiMember &&
                ((PsiMember)referent).hasModifierProperty(PsiModifier.STATIC) &&
                referent != expression.resolve()) {
              expression.bindToElement(referent);
            }
          }
        }
        expression.putUserData(TEMP_REFERENT_USER_DATA, null);
      }
    });
  }

  @Override
  protected boolean isStopElement(PsiElement element) {
    return super.isStopElement(element) || element instanceof GrReferenceExpression;
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof GrReferenceExpression)) return false;
        final GrReferenceExpression ref = (GrReferenceExpression)element;
        if (ref.getQualifier() == null) return false;
        final PsiElement resolved = ref.resolve();
        if (resolved == null) return false;
        return resolved instanceof PsiMember && !(resolved instanceof PsiClass) &&
               ((PsiMember)resolved).hasModifierProperty(PsiModifier.STATIC) &&
               ((PsiMember)resolved).getContainingClass() != null;
      }
    };
  }
}
