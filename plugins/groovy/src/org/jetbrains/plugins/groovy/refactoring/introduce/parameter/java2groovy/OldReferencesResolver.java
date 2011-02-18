/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.introduce.parameter.java2groovy;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.Map;
import java.util.Set;

import static com.intellij.codeInsight.ChangeContextUtil.*;

/**
 * @author Maxim.Medvedev
 *         Date: Apr 18, 2009 3:21:45 PM
 */

public class OldReferencesResolver {
  private final GrCall myContext;
  private final GrExpression myExpr;
  private final HashMap<GrExpression, String> myTempVars;
  private final GrExpression myInstanceRef;
  private final GrExpression[] myActualArgs;
  private final PsiMethod myMethodToReplaceIn;
  private final Project myProject;
  private final int myReplaceFieldsWithGetters;
  private final PsiExpression myParameterInitializer;
  private final PsiManager myManager;

  public OldReferencesResolver(GrCall context,
                               GrExpression expr,
                               PsiMethod methodToReplaceIn,
                               int replaceFieldsWithGetters,
                               PsiExpression parameterInitializer) throws IncorrectOperationException {
    myContext = context;
    myExpr = expr;
    myReplaceFieldsWithGetters = replaceFieldsWithGetters;
    myParameterInitializer = parameterInitializer;
    myTempVars = new HashMap<GrExpression, String>();
    myActualArgs = myContext.getArgumentList().getExpressionArguments();
    myMethodToReplaceIn = methodToReplaceIn;
    myProject = myContext.getProject();
    myManager = myContext.getManager();

    if (myContext instanceof GrMethodCallExpression) {
      final GrMethodCallExpression methodCall = (GrMethodCallExpression)myContext;
      final GrExpression methodExpression = methodCall.getInvokedExpression();
      if (methodExpression instanceof GrReferenceExpression) {
        myInstanceRef = ((GrReferenceExpression)methodExpression).getQualifierExpression();
      }
      else {
        myInstanceRef = null;
      }
    }
    else {
      myInstanceRef = null;
    }
  }

  public void resolve() throws IncorrectOperationException {
    resolveOldReferences(myExpr, myParameterInitializer);

    Set<Map.Entry<GrExpression, String>> mappingsSet = myTempVars.entrySet();

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myProject);

    for (Map.Entry<GrExpression, String> entry : mappingsSet) {
      GrExpression oldRef = entry.getKey();
      PsiElement newRef = factory.createExpressionFromText(entry.getValue());
      oldRef.replace(newRef);
    }
  }


  private void resolveOldReferences(PsiElement expr, PsiElement oldExpr) throws IncorrectOperationException {
    if (expr == null || !expr.isValid() || oldExpr == null) return;
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myProject);
    PsiElement newExpr = expr;  // references continue being resolved in the children of newExpr

    if (oldExpr instanceof PsiReferenceExpression) {
      final PsiReferenceExpression oldRef = (PsiReferenceExpression)oldExpr;
      newExpr = newExpr.replace(decodeReferenceExpression((GrReferenceExpression)newExpr, oldRef));
      //newExpr = ((GrReferenceExpression)newExpr).getReferenceNameElement();
      final JavaResolveResult adv = oldRef.advancedResolve(false);
      final PsiElement scope = getClassContainingResolve(adv);
      final PsiElement owner = PsiTreeUtil.getParentOfType(oldExpr, PsiClass.class);

      if (owner != null && scope != null && PsiTreeUtil.isAncestor(owner, scope, false)) {

        final PsiElement subj = adv.getElement();

        // Parameters
        if (subj instanceof PsiParameter) {
          PsiParameterList parameterList = myMethodToReplaceIn.getParameterList();
          PsiParameter[] parameters = parameterList.getParameters();

          if (subj.getParent() != parameterList) return;
          int index = parameterList.getParameterIndex((PsiParameter)subj);
          if (index < 0) return;
          if (index < parameters.length) {
            GrExpression actualArg = myActualArgs[index];
            int copyingSafetyLevel = GroovyRefactoringUtil.verifySafeCopyExpression(actualArg);
            if (copyingSafetyLevel == RefactoringUtil.EXPR_COPY_PROHIBITED) {
              actualArg = factory.createExpressionFromText(getTempVar(actualArg));
            }
            newExpr = newExpr.replace(actualArg);
          }
        }
        // "naked" field and methods  (should become qualified)
        else if ((subj instanceof PsiField || subj instanceof PsiMethod) && oldRef.getQualifierExpression() == null) {

          boolean isStatic = subj instanceof PsiField && ((PsiField)subj).hasModifierProperty(PsiModifier.STATIC) ||
                             subj instanceof PsiMethod && ((PsiMethod)subj).hasModifierProperty(PsiModifier.STATIC);

          if (myInstanceRef != null && !isStatic) {
            String name = ((PsiNamedElement)subj).getName();
            GrReferenceExpression newRef = (GrReferenceExpression)factory.createExpressionFromText("a." + name);
            GrExpression instanceRef = getInstanceRef(factory);
            newRef.getQualifierExpression().replace(instanceRef);
            newRef = (GrReferenceExpression)CodeStyleManager.getInstance(myProject).reformat(newRef);

            newRef = (GrReferenceExpression)newExpr.replace(newRef);
            newExpr = newRef.getReferenceNameElement();
          }
        }

        if (subj instanceof PsiField) {
          // probably replacing field with a getter
          if (myReplaceFieldsWithGetters != IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE) {
            if (myReplaceFieldsWithGetters == IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL ||
                myReplaceFieldsWithGetters == IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE &&
                !JavaPsiFacade.getInstance(myProject).getResolveHelper().isAccessible((PsiMember)subj, newExpr, null)) {
              newExpr = replaceFieldWithGetter(newExpr, (PsiField)subj);
            }
          }
        }
      }
    }
    else if (oldExpr instanceof PsiThisExpression &&
             (((PsiThisExpression)oldExpr).getQualifier() == null ||
              myManager
                .areElementsEquivalent(((PsiThisExpression)oldExpr).getQualifier().resolve(), myMethodToReplaceIn.getContainingClass()))) {
      if (myInstanceRef != null) {
        newExpr.replace(getInstanceRef(factory));
      }
      return;
    }
    else if (oldExpr instanceof PsiSuperExpression && ((PsiSuperExpression)oldExpr).getQualifier() == null) {
      if (myInstanceRef != null) {
        newExpr.replace(getInstanceRef(factory));
      }
      return;
    }
    else {
      PsiClass refClass = oldExpr.getCopyableUserData(REF_CLASS_KEY);
      if (refClass != null && refClass.isValid()) {
        PsiReference ref = newExpr.getReference();
        if (ref != null) {
          final String qualifiedName = refClass.getQualifiedName();
          if (qualifiedName != null) {
            if (JavaPsiFacade.getInstance(refClass.getProject()).findClass(qualifiedName, oldExpr.getResolveScope()) != null) {
              newExpr = ref.bindToElement(refClass);
            }
          }
        }
      }
    }


    PsiElement[] oldChildren = oldExpr.getChildren();
    PsiElement[] newChildren = newExpr.getChildren();

    if (oldExpr instanceof PsiNewExpression && newExpr instanceof GrNewExpression) { //special new-expression case
      resolveOldReferences(((GrNewExpression)newExpr).getReferenceElement(),
                           ((PsiNewExpression)oldExpr).getClassOrAnonymousClassReference());

      resolveOldReferences(((GrNewExpression)newExpr).getArgumentList(), ((PsiNewExpression)oldExpr).getArgumentList());
      if (newChildren[1] instanceof GrArrayDeclaration) {
        int j = 3; //array dimension expressions may occur since 3 position 
        for (GrExpression expression : ((GrArrayDeclaration)newChildren[1]).getBoundExpressions()) {
          while (!(oldChildren[j] instanceof CompositePsiElement)) j++;
          resolveOldReferences(expression, oldChildren[j]);
          j++;
        }
      }
    }
    else {
      if (oldExpr instanceof PsiReferenceExpression && newExpr instanceof GrReferenceExpression) {
        final PsiExpression oldQualifier = ((PsiReferenceExpression)oldExpr).getQualifierExpression();
        final GrExpression newQualifier = ((GrReferenceExpression)newExpr).getQualifierExpression();
        if (oldQualifier != null && newQualifier != null) {
          resolveOldReferences(newQualifier, oldQualifier);
          return;
        }
      }

      int oldCount = countOldChildren(oldChildren);
      if (oldCount == newChildren.length) {
        int j = 0;
        for (int i = 0; i < newChildren.length; i++) {
          while (!(oldChildren[j] instanceof CompositePsiElement)) j++;
          resolveOldReferences(newChildren[i], oldChildren[j]);
          j++;
        }
      }

    }
  }

  private static int countOldChildren(PsiElement[] children) {
    int count = 0;
    for (PsiElement child : children) {
      if (child instanceof CompositePsiElement) count++;
    }
    return count;
  }

  private GrExpression getInstanceRef(GroovyPsiElementFactory factory) throws IncorrectOperationException {
    int copyingSafetyLevel = GroovyRefactoringUtil.verifySafeCopyExpression(myInstanceRef);

    GrExpression instanceRef = myInstanceRef;
    if (copyingSafetyLevel == RefactoringUtil.EXPR_COPY_PROHIBITED) {
      instanceRef = factory.createExpressionFromText(getTempVar(myInstanceRef));
    }
    return instanceRef;
  }

  private String getTempVar(GrExpression expr) throws IncorrectOperationException {
    String id = myTempVars.get(expr);
    if (id != null) {
      return id;
    }
    else {
      id = GroovyRefactoringUtil.createTempVar(expr, myContext, true);
      myTempVars.put(expr, id);
      return id;
    }
  }

  private PsiElement replaceFieldWithGetter(PsiElement expr, PsiField psiField) throws IncorrectOperationException {
    if (RefactoringUtil.isAssignmentLHS(expr)) {
      // todo: warning
      return expr;
    }
    PsiElement newExpr = expr;

    PsiMethod getterPrototype = PropertyUtil.generateGetterPrototype(psiField);

    PsiMethod getter = psiField.getContainingClass().findMethodBySignature(getterPrototype, true);

    if (getter != null) {
      if (JavaPsiFacade.getInstance(psiField.getProject()).getResolveHelper().isAccessible(getter, newExpr, null)) {
        GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(newExpr.getProject());
        String id = getter.getName();
        final PsiElement parent = newExpr.getParent();
        String qualifier = null;
        if (parent instanceof GrReferenceExpression) {
          final GrExpression qualifierExpression = ((GrReferenceExpression)parent).getQualifierExpression();
          if (qualifierExpression != null) {
            qualifier = qualifierExpression.getText();
          }
        }
        GrMethodCallExpression getterCall =
          (GrMethodCallExpression)factory.createExpressionFromText((qualifier != null ? qualifier + "." : "") + id + "()");
        getterCall = (GrMethodCallExpression)CodeStyleManager.getInstance(myProject).reformat(getterCall);
        if (parent != null) {
          newExpr = parent.replace(getterCall);
        }
        else {
          newExpr = getterCall;
        }
      }
      else {
        // todo: warning
      }
    }

    return newExpr;
  }

  @Nullable
  private static PsiElement getClassContainingResolve(final JavaResolveResult result) {
    final PsiElement elem = result.getElement();
    if (elem != null) {
      if (elem instanceof PsiLocalVariable || elem instanceof PsiParameter) {
        return PsiTreeUtil.getParentOfType(elem, PsiClass.class);
      }
      else {
        return result.getCurrentFileResolveScope();
      }
    }
    return null;
  }

  private static GrReferenceExpression decodeReferenceExpression(GrReferenceExpression newExpr, PsiReferenceExpression refExpr)
    throws IncorrectOperationException {
    PsiManager manager = refExpr.getManager();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(manager.getProject());

    PsiExpression qualifier = refExpr.getQualifierExpression();
    if (qualifier == null) {
      PsiMember refMember = refExpr.getCopyableUserData(REF_MEMBER_KEY);
      refExpr.putCopyableUserData(REF_MEMBER_KEY, null);

      if (refMember != null && refMember.isValid()) {
        PsiClass containingClass = refMember.getContainingClass();
        if (refMember.hasModifierProperty(PsiModifier.STATIC)) {
          PsiElement refElement = newExpr.resolve();
          if (!manager.areElementsEquivalent(refMember, refElement)) {
            newExpr.setQualifier(factory.createReferenceExpressionFromText("" + containingClass.getQualifiedName()));
          }
        }
      }
      else {
        PsiClass refClass = refExpr.getCopyableUserData(REF_CLASS_KEY);
        refExpr.putCopyableUserData(REF_CLASS_KEY, null);
        if (refClass != null && refClass.isValid()) {
          newExpr = (GrReferenceExpression)newExpr.bindToElement(refClass);
        }
      }
    }
    else {
      Boolean couldRemove = refExpr.getCopyableUserData(CAN_REMOVE_QUALIFIER_KEY);
      refExpr.putCopyableUserData(CAN_REMOVE_QUALIFIER_KEY, null);

      if (couldRemove == Boolean.FALSE && canRemoveQualifier(refExpr)) {
        GrReferenceExpression newRefExpr = (GrReferenceExpression)factory.createExpressionFromText(refExpr.getReferenceName());
        newExpr = (GrReferenceExpression)newExpr.replace(newRefExpr);
      }
    }
    return newExpr;
  }

  private static boolean canRemoveQualifier(PsiReferenceExpression refExpr) {
    try {
      PsiExpression qualifier = refExpr.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) return false;

      PsiElement qualifierRefElement = ((PsiReferenceExpression)qualifier).resolve();
      if (!(qualifierRefElement instanceof PsiClass)) return false;

      PsiElement refElement = refExpr.resolve();
      if (refElement == null) return false;

      PsiElementFactory factory = JavaPsiFacade.getInstance(refExpr.getProject()).getElementFactory();

      if (refExpr.getParent() instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression methodCall = (PsiMethodCallExpression)refExpr.getParent();
        PsiMethodCallExpression newMethodCall =
          (PsiMethodCallExpression)factory.createExpressionFromText(refExpr.getReferenceName() + "()", refExpr);
        newMethodCall.getArgumentList().replace(methodCall.getArgumentList());
        PsiElement newRefElement = newMethodCall.getMethodExpression().resolve();
        return refElement.equals(newRefElement);
      }
      else {
        PsiReferenceExpression newRefExpr = (PsiReferenceExpression)factory.createExpressionFromText(refExpr.getReferenceName(), refExpr);
        PsiElement newRefElement = newRefExpr.resolve();
        return refElement.equals(newRefElement);
      }
    }
    catch (IncorrectOperationException e) {
      return false;
    }
  }
}