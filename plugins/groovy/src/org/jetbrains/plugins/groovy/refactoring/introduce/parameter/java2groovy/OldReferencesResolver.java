/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 *         Date: Apr 18, 2009 3:21:45 PM
 */

public class OldReferencesResolver {
  private static final Logger LOG = Logger.getInstance(OldReferencesResolver.class);

  private final GrCall myContext;
  private final GrExpression myExpr;
  private final HashMap<GrExpression, String> myTempVars;
  private final GrExpression myInstanceRef;
  private final GrClosureSignatureUtil.ArgInfo<PsiElement>[] myActualArgs;
  private final PsiElement myToReplaceIn;
  private final Project myProject;
  private final int myReplaceFieldsWithGetters;
  private final PsiElement myParameterInitializer;
  private final PsiManager myManager;
  private final PsiParameter[] myParameters;
  private final GrClosureSignature mySignature;
  
  private final Set<PsiParameter> myParamsToNotInline = new HashSet<>();

  public OldReferencesResolver(GrCall context,
                               GrExpression expr,
                               PsiElement toReplaceIn,
                               int replaceFieldsWithGetters,
                               PsiElement parameterInitializer,
                               final GrClosureSignature signature,
                               final GrClosureSignatureUtil.ArgInfo<PsiElement>[] actualArgs, PsiParameter[] parameters) throws IncorrectOperationException {
    myContext = context;
    myExpr = expr;
    myReplaceFieldsWithGetters = replaceFieldsWithGetters;
    myParameterInitializer = parameterInitializer;
    myParameters = parameters;
    myTempVars = new HashMap<>();
    mySignature = signature;
    myActualArgs = actualArgs;
    myToReplaceIn = toReplaceIn;
    myProject = myContext.getProject();
    myManager = myContext.getManager();

    if (myContext instanceof GrMethodCallExpression) {
      final GrMethodCallExpression methodCall = (GrMethodCallExpression)myContext;
      final GrExpression methodExpression = methodCall.getInvokedExpression();
      if (methodExpression instanceof GrReferenceExpression) {
        myInstanceRef = ((GrReferenceExpression)methodExpression).getQualifierExpression();
      }
      else if (methodExpression instanceof GrMethodCall) {
        myInstanceRef = getQualifierFromGetterCall((GrMethodCall)methodExpression);
      }
      else {
        myInstanceRef = null;
      }
    }
    else {
      myInstanceRef = null;
    }
  }

  /**
   * checks for the case: qualifier.getFoo()(args)
   * @param methodExpression
   */
  @Nullable
  private static GrExpression getQualifierFromGetterCall(GrMethodCall methodExpression) {
    final GroovyResolveResult result = methodExpression.advancedResolve();
    if (!(result.getElement() instanceof GrAccessorMethod) || result.isInvokedOnProperty()) return null;

    final GrExpression invoked = methodExpression.getInvokedExpression();
    if (invoked instanceof GrReferenceExpression) return ((GrReferenceExpression)invoked).getQualifier();

    return null;
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

    if (oldExpr instanceof GrReferenceExpression) {
      if (isThisReferenceToContainingClass(oldExpr) || isSimpleSuperReference(oldExpr)) {
        if (myInstanceRef != null) {
          newExpr.replace(getInstanceRef(factory));
        }
        return;
      }

      final GrReferenceExpression oldRef = (GrReferenceExpression)oldExpr;
      newExpr = newExpr.replace(decodeReferenceExpression((GrReferenceExpression)newExpr, oldRef));
      //newExpr = ((GrReferenceExpression)newExpr).getReferenceNameElement();
      final GroovyResolveResult adv = oldRef.advancedResolve();
      final PsiElement scope = getClassContainingResolve(adv);
      final PsiElement owner = PsiUtil.getContextClass(oldExpr);

      if (myToReplaceIn instanceof GrClosableBlock || (owner != null && scope != null && PsiTreeUtil.isContextAncestor(owner, scope, false))) {

        final PsiElement subj = adv.getElement();

        // Parameters
        if (subj instanceof PsiParameter) {
          int index = ArrayUtil.indexOf(myParameters, subj);
          if (index < 0) return;
          if (index < myParameters.length) {
            newExpr = inlineParam(newExpr, getActualArg(index), ((PsiParameter)subj));
          }
        }
        // "naked" field and methods  (should become qualified)
        else if ((subj instanceof PsiField || subj instanceof PsiMethod) && oldRef.getQualifierExpression() == null) {

          PsiElement newResolved = newExpr instanceof GrReferenceExpression ? ((GrReferenceExpression)newExpr).resolve() : null;
          if (myInstanceRef != null || !subj.getManager().areElementsEquivalent(newResolved, subj)) {
            boolean isStatic = subj instanceof PsiField && ((PsiField)subj).hasModifierProperty(PsiModifier.STATIC) ||
                               subj instanceof PsiMethod && ((PsiMethod)subj).hasModifierProperty(PsiModifier.STATIC);

            String name = ((PsiNamedElement)subj).getName();
            boolean shouldBeAt = subj instanceof PsiField &&
                                 !PsiTreeUtil.isAncestor(((PsiMember)subj).getContainingClass(), newExpr, true) &&
                                 GroovyPropertyUtils.findGetterForField((PsiField)subj) != null;
            final GrReferenceExpression fromText = factory.createReferenceExpressionFromText("qualifier." + (shouldBeAt ? "@" : "") + name);
            if (isStatic) {
              final GrReferenceExpression qualifier = factory.createReferenceElementForClass(((PsiMember)subj).getContainingClass());
              newExpr = newExpr.replace(fromText);
              ((GrReferenceExpression)newExpr).setQualifier(qualifier);
              newExpr = ((GrReferenceExpression)newExpr).getReferenceNameElement();
            }
            else {
              if (myInstanceRef != null) {
                GrExpression instanceRef = getInstanceRef(factory);
                fromText.setQualifier(instanceRef);
                newExpr = newExpr.replace(fromText);
                newExpr = ((GrReferenceExpression)newExpr).getReferenceNameElement();
              }
            }
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
    else {
      PsiClass refClass = oldExpr.getCopyableUserData(ChangeContextUtil.REF_CLASS_KEY);
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

    if (oldExpr instanceof GrNewExpression && newExpr instanceof GrNewExpression) { //special new-expression case
      resolveOldReferences(((GrNewExpression)newExpr).getReferenceElement(),
                           ((GrNewExpression)oldExpr).getReferenceElement());

      resolveOldReferences(((GrNewExpression)newExpr).getArgumentList(), ((GrNewExpression)oldExpr).getArgumentList());
      if (newChildren[1] instanceof GrArrayDeclaration) {
        for (GrExpression expression : ((GrArrayDeclaration)newChildren[1]).getBoundExpressions()) {
          resolveOldReferences(expression, oldChildren[1]);
        }
      }
    }
    else {
      if (oldExpr instanceof GrReferenceExpression && newExpr instanceof GrReferenceExpression) {
        final GrExpression oldQualifier = ((GrReferenceExpression)oldExpr).getQualifierExpression();
        final GrExpression newQualifier = ((GrReferenceExpression)newExpr).getQualifierExpression();
        if (oldQualifier != null && newQualifier != null) {
          resolveOldReferences(newQualifier, oldQualifier);
          return;
        }
      }

      if (oldChildren.length == newChildren.length) {
        for (int i = 0; i < newChildren.length; i++) {
          resolveOldReferences(newChildren[i], oldChildren[i]);
        }
      }
    }
  }

  private PsiElement inlineParam(PsiElement newExpr, GrExpression actualArg, PsiParameter parameter) {
    if (myParamsToNotInline.contains(parameter)) return newExpr;
    
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myProject);

    if (myExpr instanceof GrClosableBlock) {
      int count = 0;
      for (PsiReference reference : ReferencesSearch.search(parameter, new LocalSearchScope(myParameterInitializer))) {
        count++;
        if (count > 1) break;
      }
      if (count > 1) {
        myParamsToNotInline.add(parameter);

        final PsiType type;
        if (parameter instanceof GrParameter) {
          type = ((GrParameter)parameter).getDeclaredType();
        }
        else {
          type = parameter.getType();
        }
        final GrVariableDeclaration declaration =
          factory.createVariableDeclaration(ArrayUtil.EMPTY_STRING_ARRAY, actualArg, type, parameter.getName());

        final GrStatement[] statements = ((GrClosableBlock)myExpr).getStatements();
        GrStatement anchor = statements.length > 0 ? statements[0] : null;
        return ((GrClosableBlock)myExpr).addStatementBefore(declaration, anchor);
      }
    }


    int copyingSafetyLevel = GroovyRefactoringUtil.verifySafeCopyExpression(actualArg);
    if (copyingSafetyLevel == RefactoringUtil.EXPR_COPY_PROHIBITED) {
      actualArg = factory.createExpressionFromText(getTempVar(actualArg));
    }
    newExpr = newExpr.replace(actualArg);
    return newExpr;
  }

  private static boolean isSimpleSuperReference(PsiElement oldExpr) {
    if (oldExpr instanceof GrReferenceExpression) {
      GrReferenceExpression ref = (GrReferenceExpression)oldExpr;
      if (ref.getQualifier() == null) {
        PsiElement nameElement = ref.getReferenceNameElement();
        if (nameElement != null) {
          return nameElement.getNode().getElementType() == GroovyTokenTypes.kSUPER;
        }
      }
    }
    return false;
  }

  private boolean isThisReferenceToContainingClass(PsiElement oldExpr) {
    if (!(oldExpr instanceof GrReferenceExpression && PsiUtil.isThisReference(oldExpr))) return false;

    final GrReferenceExpression qualifier = (GrReferenceExpression)((GrReferenceExpression)oldExpr).getQualifier();
    if (qualifier == null) return true;

    final PsiClass contextClass = PsiUtil.getContextClass(myToReplaceIn);
    final PsiElement resolved = qualifier.resolve();
    return myManager.areElementsEquivalent(resolved, contextClass);
  }

  @NotNull
  private GrExpression getActualArg(int index) {
    if (myActualArgs == null || myActualArgs[index] == null) {
      final GrExpression[] arguments = myContext.getArgumentList().getExpressionArguments();
      if (index < arguments.length) return arguments[index];
      index -= arguments.length;
      final GrClosableBlock[] closureArguments = myContext.getClosureArguments();
      if (index < closureArguments.length) return closureArguments[index];
      throw new IncorrectOperationException("fail :(");
    }

    final GrClosureSignatureUtil.ArgInfo<PsiElement> argInfo = myActualArgs[index];
    final List<PsiElement> args = argInfo.args;
    if (argInfo.isMultiArg) {
      return GroovyRefactoringUtil.generateArgFromMultiArg(mySignature.getSubstitutor(), args, myParameters[index].getType(),
                                                           myContext.getProject());
    }
    else if (args.isEmpty()) {
      final PsiParameter parameter = myParameters[index];
      LOG.assertTrue(parameter instanceof GrParameter);
      final GrExpression initializer = ((GrParameter)parameter).getInitializerGroovy();
      LOG.assertTrue(initializer != null);
      return (GrExpression)initializer.copy();
    }
    else {
      return (GrExpression)args.get(0);
    }
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

  private static PsiElement replaceFieldWithGetter(PsiElement expr, PsiField psiField) throws IncorrectOperationException {
    if (RefactoringUtil.isAssignmentLHS(expr)) {
      // todo: warning
      return expr;
    }
    PsiElement newExpr = expr;

    PsiMethod getter = GroovyPropertyUtils.findGetterForField(psiField);

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
        GrExpression getterCall;
        if (PsiTreeUtil.isAncestor(psiField.getContainingClass(), expr, true)) {
          getterCall = factory.createExpressionFromText((qualifier != null ? qualifier + "." : "") + id + "()");
        }
        else {
          getterCall = factory.createExpressionFromText((qualifier != null ? qualifier + "." : "") + psiField.getName());
        }
        if (parent != null) {
          newExpr = parent.replace(getterCall);
        }
        else {
          newExpr = expr.replace(getterCall);
        }
      }
      else {
        // todo: warning
      }
    }

    return newExpr;
  }

  @Nullable
  private static PsiElement getClassContainingResolve(final GroovyResolveResult result) {
    final PsiElement elem = result.getElement();
    if (elem != null) {
      if (elem instanceof PsiMember) {
        return ((PsiMember)elem).getContainingClass();
      }
      else {
        return PsiUtil.getContextClass(elem);
      }
    }
    return null;
  }

  private static GrReferenceExpression decodeReferenceExpression(GrReferenceExpression newExpr, GrReferenceExpression refExpr)
    throws IncorrectOperationException {
    PsiManager manager = refExpr.getManager();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(manager.getProject());

    GrExpression qualifier = refExpr.getQualifier();
    if (qualifier == null) {
      PsiMember refMember = refExpr.getCopyableUserData(ChangeContextUtil.REF_MEMBER_KEY);
      refExpr.putCopyableUserData(ChangeContextUtil.REF_MEMBER_KEY, null);

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
        PsiClass refClass = refExpr.getCopyableUserData(ChangeContextUtil.REF_CLASS_KEY);
        refExpr.putCopyableUserData(ChangeContextUtil.REF_CLASS_KEY, null);
        if (refClass != null && refClass.isValid()) {
          newExpr = (GrReferenceExpression)newExpr.bindToElement(refClass);
        }
      }
    }
    else {
      Boolean couldRemove = refExpr.getCopyableUserData(ChangeContextUtil.CAN_REMOVE_QUALIFIER_KEY);
      refExpr.putCopyableUserData(ChangeContextUtil.CAN_REMOVE_QUALIFIER_KEY, null);

      if (couldRemove == Boolean.FALSE && canRemoveQualifier(refExpr)) {
        GrReferenceExpression newRefExpr = (GrReferenceExpression)factory.createExpressionFromText(refExpr.getReferenceName());
        newExpr = (GrReferenceExpression)newExpr.replace(newRefExpr);
      }
    }
    return newExpr;
  }

  private static boolean canRemoveQualifier(GrReferenceExpression refExpr) {
    try {
      GrExpression qualifier = refExpr.getQualifier();
      if (!(qualifier instanceof GrReferenceExpression)) return false;

      PsiElement qualifierRefElement = ((GrReferenceExpression)qualifier).resolve();
      if (!(qualifierRefElement instanceof PsiClass)) return false;

      PsiElement refElement = refExpr.resolve();
      if (refElement == null) return false;

      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(refExpr.getProject());

      if (refExpr.getParent() instanceof GrMethodCallExpression) {
        GrMethodCallExpression methodCall = (GrMethodCallExpression)refExpr.getParent();
        GrMethodCallExpression newMethodCall =
          (GrMethodCallExpression)factory.createExpressionFromText(refExpr.getReferenceName() + "()", refExpr);
        newMethodCall.getArgumentList().replace(methodCall.getArgumentList());
        PsiElement newRefElement = ((GrReferenceExpression)newMethodCall.getInvokedExpression()).resolve();
        return refElement.equals(newRefElement);
      }
      else {
        GrReferenceExpression newRefExpr = (GrReferenceExpression)factory.createExpressionFromText(refExpr.getReferenceName(), refExpr);
        PsiElement newRefElement = newRefExpr.resolve();
        return refElement.equals(newRefElement);
      }
    }
    catch (IncorrectOperationException e) {
      return false;
    }
  }
}
