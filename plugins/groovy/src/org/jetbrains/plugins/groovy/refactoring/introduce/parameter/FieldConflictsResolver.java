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
package org.jetbrains.plugins.groovy.refactoring.introduce.parameter;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class FieldConflictsResolver {
  private static final Logger LOG =
    Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.introduce.parameter.FieldConflictsResolver");
  private final GrCodeBlock myScope;
  private PsiField myField = null;
  private List<GrReferenceExpression> myReferenceExpressions = null;
  private PsiClass myQualifyingClass;

  public FieldConflictsResolver(String name, GrCodeBlock scope) {
    myScope = scope;
    if (myScope == null) return;

    final GroovyPsiElement resolved = ResolveUtil.resolveProperty(myScope, name);
    if (resolved instanceof GrReferenceExpression || resolved == null) return;

    assert  resolved instanceof PsiVariable;
    final PsiVariable oldVariable = (PsiVariable)resolved;
    myField = oldVariable instanceof PsiField ? (PsiField) oldVariable : null;
    if (!(oldVariable instanceof PsiField)) return;

    myReferenceExpressions = new ArrayList<>();
    for (PsiReference reference : ReferencesSearch.search(myField, new LocalSearchScope(myScope), false)) {
      final PsiElement element = reference.getElement();
      if (element instanceof GrReferenceExpression) {
        final GrReferenceExpression referenceExpression = (GrReferenceExpression)element;
        if (referenceExpression.getQualifier() == null) {
          myReferenceExpressions.add(referenceExpression);
        }
      }
    }
    if (myField.hasModifierProperty(PsiModifier.STATIC)) {
      myQualifyingClass = myField.getContainingClass();
    }
  }

  public GrExpression fixInitializer(GrExpression initializer) {
    if (myField == null) return initializer;
    final GrReferenceExpression[] replacedRef = {null};
    initializer.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(GrReferenceExpression expression) {
        final GrExpression qualifierExpression = expression.getQualifier();
        if (qualifierExpression != null) {
          qualifierExpression.accept(this);
        }
        else {
          final PsiElement result = expression.resolve();
          if (expression.getManager().areElementsEquivalent(result, myField)) {
            try {
              replacedRef[0] = qualifyReference(expression, myField, myQualifyingClass);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }
    });
    if (!initializer.isValid()) return replacedRef[0];
    return initializer;
  }

  public void fix() throws IncorrectOperationException {
    if (myField == null) return;
    final PsiManager manager = myScope.getManager();
    for (GrReferenceExpression referenceExpression : myReferenceExpressions) {
      if (!referenceExpression.isValid()) continue;
      final PsiElement newlyResolved = referenceExpression.resolve();
      if (!manager.areElementsEquivalent(newlyResolved, myField)) {
        qualifyReference(referenceExpression, myField, myQualifyingClass);
      }
    }
  }


  public static GrReferenceExpression qualifyReference(GrReferenceExpression referenceExpression,
                                                       final PsiMember member,
                                                       @Nullable final PsiClass qualifyingClass) throws IncorrectOperationException {
    PsiManager manager = referenceExpression.getManager();
    GrReferenceExpression expressionFromText;
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(referenceExpression.getProject());
    if (qualifyingClass == null) {
      PsiClass parentClass = PsiTreeUtil.getParentOfType(referenceExpression, PsiClass.class);
      final PsiClass containingClass = member.getContainingClass();
      if (parentClass != null && !InheritanceUtil.isInheritorOrSelf(parentClass, containingClass, true)) {
        while (parentClass != null && !InheritanceUtil.isInheritorOrSelf(parentClass, containingClass, true)) {
          parentClass = PsiTreeUtil.getParentOfType(parentClass, PsiClass.class, true);
        }
        LOG.assertTrue(parentClass != null);
        expressionFromText = factory.createReferenceExpressionFromText("A.this." + member.getName());
        //noinspection ConstantConditions
        ((GrReferenceExpression)expressionFromText.getQualifier()).getQualifier().replace(factory.createReferenceElementForClass(parentClass));
      }
      else {
        expressionFromText = (GrReferenceExpression)factory.createExpressionFromText("this." + member.getName());
      }
    }
    else {
      expressionFromText = (GrReferenceExpression)factory.createExpressionFromText("A." + member.getName());
      expressionFromText.setQualifier(factory.createReferenceElementForClass(qualifyingClass));
    }
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
    expressionFromText = (GrReferenceExpression)codeStyleManager.reformat(expressionFromText);
    return (GrReferenceExpression)referenceExpression.replace(expressionFromText);
  }
}
