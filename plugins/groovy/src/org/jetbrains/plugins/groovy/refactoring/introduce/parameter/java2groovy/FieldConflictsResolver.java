// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.refactoring.introduce.parameter.java2groovy;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class FieldConflictsResolver {
  private final GrCodeBlock myScope;
  private final PsiField myField;
  private final List<GrReferenceExpression> myReferenceExpressions;
  private PsiClass myQualifyingClass;

  public FieldConflictsResolver(String name, GrCodeBlock scope) {
    myScope = scope;
    if (myScope == null) {
      myField = null;
      myReferenceExpressions = null;
      return;
    }
    JavaPsiFacade facade = JavaPsiFacade.getInstance(myScope.getProject());
    final PsiVariable oldVariable = facade.getResolveHelper().resolveAccessibleReferencedVariable(name, myScope);
    myField = oldVariable instanceof PsiField ? (PsiField)oldVariable : null;

    if (!(oldVariable instanceof PsiField)) {
      myReferenceExpressions = null;
      return;
    }
    myReferenceExpressions = new ArrayList<>();
    for (PsiReference reference : ReferencesSearch.search(myField, new LocalSearchScope(myScope), false).asIterable()) {
      final PsiElement element = reference.getElement();
      if (element instanceof GrReferenceExpression referenceExpression) {
        if (referenceExpression.getQualifierExpression() == null) {
          myReferenceExpressions.add(referenceExpression);
        }
      }
    }
    if (myField.hasModifierProperty(PsiModifier.STATIC)) {
      myQualifyingClass = myField.getContainingClass();
    }
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
                                                       final @Nullable PsiClass qualifyingClass) throws IncorrectOperationException {
    PsiManager manager = referenceExpression.getManager();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(manager.getProject());

    GrReferenceExpression expressionFromText;
    if (qualifyingClass == null) {
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(referenceExpression, PsiClass.class);
      final PsiClass containingClass = member.getContainingClass();
      if (parentClass != null && !InheritanceUtil.isInheritorOrSelf(parentClass, containingClass, true)) {
        expressionFromText = (GrReferenceExpression)factory.createExpressionFromText(containingClass.getQualifiedName()+ ".this." + member.getName());
      }
      else {
        expressionFromText = (GrReferenceExpression)factory.createExpressionFromText("this." + member.getName());
      }
    }
    else {
      expressionFromText = (GrReferenceExpression)factory.createExpressionFromText(qualifyingClass.getQualifiedName()+ '.' + member.getName());
    }
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
    expressionFromText = (GrReferenceExpression)codeStyleManager.reformat(expressionFromText);
    return (GrReferenceExpression)referenceExpression.replace(expressionFromText);
  }

}
