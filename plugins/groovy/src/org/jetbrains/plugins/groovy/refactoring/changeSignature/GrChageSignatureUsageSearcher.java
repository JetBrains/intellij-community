/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.*;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.refactoring.util.usageInfo.NoConstructorClassUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTagValueToken;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
class GrChageSignatureUsageSearcher {
  private final JavaChangeInfo myChangeInfo;
  private static final Logger LOG =
    Logger.getInstance("org.jetbrains.plugins.groovy.refactoring.changeSignature.GrChageSignatureUsageSearcher");

  GrChageSignatureUsageSearcher(JavaChangeInfo changeInfo) {
    this.myChangeInfo = changeInfo;
  }

  public UsageInfo[] findUsages() {
    ArrayList<UsageInfo> result = new ArrayList<>();
    final PsiElement element = myChangeInfo.getMethod();
    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;

      findSimpleUsages(method, result);

      final UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
      return UsageViewUtil.removeDuplicatedUsages(usageInfos);
    }
    return UsageInfo.EMPTY_ARRAY;
  }


  private void findSimpleUsages(final PsiMethod method, final ArrayList<UsageInfo> result) {
    PsiMethod[] overridingMethods = findSimpleUsagesWithoutParameters(method, result, true, true, true);
    //findUsagesInCallers(result); todo

    //Parameter name changes are not propagated
    findParametersUsage(method, result, overridingMethods);
  }

  /* todo
  private void findUsagesInCallers(final ArrayList<UsageInfo> usages) {
    if (myChangeInfo instanceof JavaChangeInfoImpl) {
      JavaChangeInfoImpl changeInfo = (JavaChangeInfoImpl)myChangeInfo;

      for (PsiMethod caller : changeInfo.propagateParametersMethods) {
        usages.add(new CallerUsageInfo(caller, true, changeInfo.propagateExceptionsMethods.contains(caller)));
      }
      for (PsiMethod caller : changeInfo.propagateExceptionsMethods) {
        usages.add(new CallerUsageInfo(caller, changeInfo.propagateParametersMethods.contains(caller), true));
      }
      Set<PsiMethod> merged = new HashSet<PsiMethod>();
      merged.addAll(changeInfo.propagateParametersMethods);
      merged.addAll(changeInfo.propagateExceptionsMethods);
      for (final PsiMethod method : merged) {
        findSimpleUsagesWithoutParameters(method, usages, changeInfo.propagateParametersMethods.contains(method),
                                          changeInfo.propagateExceptionsMethods.contains(method), false);
      }
    }
  }
  */

  private void detectLocalsCollisionsInMethod(final GrMethod method, final ArrayList<UsageInfo> result, boolean isOriginal) {
    if (!GroovyLanguage.INSTANCE.equals(method.getLanguage())) return;

    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final Set<PsiParameter> deletedOrRenamedParameters = new HashSet<>();
    if (isOriginal) {
      ContainerUtil.addAll(deletedOrRenamedParameters, parameters);
      for (ParameterInfo parameterInfo : myChangeInfo.getNewParameters()) {
        if (parameterInfo.getOldIndex() >= 0) {
          final PsiParameter parameter = parameters[parameterInfo.getOldIndex()];
          if (parameterInfo.getName().equals(parameter.getName())) {
            deletedOrRenamedParameters.remove(parameter);
          }
        }
      }
    }
    final GrOpenBlock block = method.getBlock();
    for (ParameterInfo parameterInfo : myChangeInfo.getNewParameters()) {
      final int oldParameterIndex = parameterInfo.getOldIndex();
      final String newName = parameterInfo.getName();
      if (oldParameterIndex >= 0) {
        if (isOriginal) {   //Name changes take place only in primary method
          final PsiParameter parameter = parameters[oldParameterIndex];
          if (!newName.equals(parameter.getName())) {
            final GrUnresolvableLocalCollisionDetector.CollidingVariableVisitor collidingVariableVisitor =
              new GrUnresolvableLocalCollisionDetector.CollidingVariableVisitor() {
                @Override
                public void visitCollidingVariable(final PsiVariable collidingVariable) {
                  if (!deletedOrRenamedParameters.contains(collidingVariable)) {
                    result.add(new RenamedParameterCollidesWithLocalUsageInfo(parameter, collidingVariable, method));
                  }
                }
              };
            if (block != null) {
              GrUnresolvableLocalCollisionDetector.visitLocalsCollisions(parameter, newName, block, collidingVariableVisitor);
            }
          }
        }
      }
      else {
        final GrUnresolvableLocalCollisionDetector.CollidingVariableVisitor variableVisitor =
          new GrUnresolvableLocalCollisionDetector.CollidingVariableVisitor() {
            @Override
            public void visitCollidingVariable(PsiVariable collidingVariable) {
              if (!deletedOrRenamedParameters.contains(collidingVariable)) {
                result.add(new NewParameterCollidesWithLocalUsageInfo(collidingVariable, collidingVariable, method));
              }
            }
          };
        if (block != null) {
          GrUnresolvableLocalCollisionDetector.visitLocalsCollisions(method, newName, block, variableVisitor);
        }
      }
    }
  }

  private void findParametersUsage(final PsiMethod method, ArrayList<UsageInfo> result, PsiMethod[] overriders) {
    if (!GroovyLanguage.INSTANCE.equals(method.getLanguage())) return;

    PsiParameter[] parameters = method.getParameterList().getParameters();
    for (ParameterInfo info : myChangeInfo.getNewParameters()) {
      if (info.getOldIndex() >= 0) {
        PsiParameter parameter = parameters[info.getOldIndex()];
        if (!info.getName().equals(parameter.getName())) {
          addParameterUsages(parameter, result, info);

          for (PsiMethod overrider : overriders) {
            if (!GroovyLanguage.INSTANCE.equals(overrider.getLanguage())) continue;
            PsiParameter parameter1 = overrider.getParameterList().getParameters()[info.getOldIndex()];
            if (parameter.getName().equals(parameter1.getName())) {
              addParameterUsages(parameter1, result, info);
            }
          }
        }
      }
    }
  }

  private PsiMethod[] findSimpleUsagesWithoutParameters(final PsiMethod method,
                                                        final ArrayList<UsageInfo> result,
                                                        boolean isToModifyArgs,
                                                        boolean isToThrowExceptions,
                                                        boolean isOriginal) {

    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(method.getProject());
    PsiMethod[] overridingMethods = OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY);

    for (PsiMethod overridingMethod : overridingMethods) {
      if (GroovyLanguage.INSTANCE.equals(overridingMethod.getLanguage())) {
        result.add(new OverriderUsageInfo(overridingMethod, method, isOriginal, isToModifyArgs, isToThrowExceptions));
      }
    }

    boolean needToChangeCalls =
      !myChangeInfo.isGenerateDelegate() && (myChangeInfo.isNameChanged() ||
                                             myChangeInfo.isParameterSetOrOrderChanged() ||
                                             myChangeInfo.isExceptionSetOrOrderChanged() ||
                                             myChangeInfo.isVisibilityChanged()/*for checking inaccessible*/);
    if (needToChangeCalls) {
      PsiReference[] refs = MethodReferencesSearch.search(method, projectScope, true).toArray(PsiReference.EMPTY_ARRAY);
      for (PsiReference ref : refs) {
        PsiElement element = ref.getElement();

        if (!GroovyLanguage.INSTANCE.equals(element.getLanguage())) continue;

        boolean isToCatchExceptions = isToThrowExceptions && needToCatchExceptions(RefactoringUtil.getEnclosingMethod(element));
        if (PsiUtil.isMethodUsage(element)) {
          result.add(new GrMethodCallUsageInfo(element, isToModifyArgs, isToCatchExceptions, method));
        }
        else if (element instanceof GrDocTagValueToken) {
          result.add(new UsageInfo(ref.getElement()));
        }
        else if (element instanceof GrMethod && ((GrMethod)element).isConstructor()) {
          DefaultConstructorImplicitUsageInfo implicitUsageInfo =
            new DefaultConstructorImplicitUsageInfo((GrMethod)element, ((GrMethod)element).getContainingClass(), method);
          result.add(implicitUsageInfo);
        }
        else if (element instanceof PsiClass) {
          LOG.assertTrue(method.isConstructor());
          final PsiClass psiClass = (PsiClass)element;
          if (psiClass instanceof GrAnonymousClassDefinition) {
            result.add(new GrMethodCallUsageInfo(element, isToModifyArgs, isToCatchExceptions, method));
            continue;
          }
          /*if (!(myChangeInfo instanceof JavaChangeInfoImpl)) continue; todo propagate methods
          if (shouldPropagateToNonPhysicalMethod(method, result, psiClass,
                                                 ((JavaChangeInfoImpl)myChangeInfo).propagateParametersMethods)) {
            continue;
          }
          if (shouldPropagateToNonPhysicalMethod(method, result, psiClass,
                                                 ((JavaChangeInfoImpl)myChangeInfo).propagateExceptionsMethods)) {
            continue;
          }*/
          result.add(new NoConstructorClassUsageInfo(psiClass));
        }
        else if (ref instanceof PsiCallReference) {
          result.add(new CallReferenceUsageInfo((PsiCallReference)ref));
        }
        else {
          result.add(new MoveRenameUsageInfo(element, ref, method));
        }
      }
    }
    else if (myChangeInfo.isParameterTypesChanged()) {
      PsiReference[] refs = MethodReferencesSearch.search(method, projectScope, true).toArray(PsiReference.EMPTY_ARRAY);
      for (PsiReference reference : refs) {
        final PsiElement element = reference.getElement();
        if (element instanceof GrDocTagValueToken) {
          result.add(new UsageInfo(reference));
        }
      }
    }

    // Conflicts
    if (method instanceof GrMethod) {
      detectLocalsCollisionsInMethod((GrMethod)method, result, isOriginal);
    }
    for (final PsiMethod overridingMethod : overridingMethods) {
      if (overridingMethod instanceof GrMethod) {
        detectLocalsCollisionsInMethod((GrMethod)overridingMethod, result, isOriginal);
      }
    }

    return overridingMethods;
  }



  private static void addParameterUsages(PsiParameter parameter, ArrayList<UsageInfo> results, ParameterInfo info) {
    for (PsiReference psiReference : ReferencesSearch.search(parameter)) {
      PsiElement parmRef = psiReference.getElement();
      UsageInfo usageInfo = new ChangeSignatureParameterUsageInfo(parmRef, parameter.getName(), info.getName());
      results.add(usageInfo);
    }
    if (info.getName() != parameter.getName()) {
      
    }
  }

  private boolean needToCatchExceptions(PsiMethod caller) {
    /*if (myChangeInfo instanceof JavaChangeInfoImpl) { //todo propagate methods
      return myChangeInfo.isExceptionSetOrOrderChanged() &&
             !((JavaChangeInfoImpl)myChangeInfo).propagateExceptionsMethods.contains(caller);
    }
    else {*/
    return myChangeInfo.isExceptionSetOrOrderChanged();
  }

  private static class RenamedParameterCollidesWithLocalUsageInfo extends UnresolvableCollisionUsageInfo {
    private final PsiElement myCollidingElement;
    private final PsiMethod myMethod;

    public RenamedParameterCollidesWithLocalUsageInfo(PsiParameter parameter, PsiElement collidingElement, PsiMethod method) {
      super(parameter, collidingElement);
      myCollidingElement = collidingElement;
      myMethod = method;
    }

    @Override
    public String getDescription() {
      return RefactoringBundle.message("there.is.already.a.0.in.the.1.it.will.conflict.with.the.renamed.parameter",
                                       RefactoringUIUtil.getDescription(myCollidingElement, true),
                                       RefactoringUIUtil.getDescription(myMethod, true));
    }
  }

}
