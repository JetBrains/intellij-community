// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.JavaChangeInfo;
import com.intellij.refactoring.changeSignature.JavaParameterInfo;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Iterator;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
class GrChangeSignatureConflictSearcher {
  private static final Logger LOG = Logger.getInstance(GrChangeSignatureConflictSearcher.class);
  private final JavaChangeInfo myChangeInfo;

  GrChangeSignatureConflictSearcher(JavaChangeInfo changeInfo) {
    myChangeInfo = changeInfo;
  }

  public MultiMap<PsiElement, String> findConflicts(Ref<UsageInfo[]> refUsages) {
    MultiMap<PsiElement, String> conflictDescriptions = new MultiMap<>();
    addMethodConflicts(conflictDescriptions);
    UsageInfo[] usagesIn = refUsages.get();
    RenameUtil.addConflictDescriptions(usagesIn, conflictDescriptions);
    Set<UsageInfo> usagesSet = ContainerUtil.newHashSet(usagesIn);
    RenameUtil.removeConflictUsages(usagesSet);
    if (myChangeInfo.isVisibilityChanged()) {
      try {
        addInaccessibilityDescriptions(usagesSet, conflictDescriptions);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    return conflictDescriptions;
  }

  private boolean needToChangeCalls() {
    return myChangeInfo.isNameChanged() || myChangeInfo.isParameterSetOrOrderChanged() || myChangeInfo.isExceptionSetOrOrderChanged();
  }

  private void addInaccessibilityDescriptions(Set<UsageInfo> usages, MultiMap<PsiElement, String> conflictDescriptions)
    throws IncorrectOperationException {
    PsiMethod method = myChangeInfo.getMethod();
    PsiModifierList modifierList = (PsiModifierList)method.getModifierList().copy();
    VisibilityUtil.setVisibility(modifierList, myChangeInfo.getNewVisibility());

    for (Iterator<UsageInfo> iterator = usages.iterator(); iterator.hasNext();) {
      UsageInfo usageInfo = iterator.next();
      PsiElement element = usageInfo.getElement();
      if (element != null) {
        if (element instanceof GrReferenceExpression) {
          PsiClass accessObjectClass = null;
          GrExpression qualifier = ((GrReferenceExpression)element).getQualifierExpression();
          if (qualifier != null) {
            accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement();
          }

          PsiResolveHelper helper = JavaPsiFacade.getInstance(element.getProject()).getResolveHelper();
          if (!helper.isAccessible(method, modifierList, element, accessObjectClass, null)) {
            String message =
              RefactoringBundle.message("0.with.1.visibility.is.not.accessible.from.2",
                                        RefactoringUIUtil.getDescription(method, true),
                                        myChangeInfo.getNewVisibility(),
                                        RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(element), true));
            conflictDescriptions.putValue(method, message);
            if (!needToChangeCalls()) {
              iterator.remove();
            }
          }
        }
      }
    }
  }

  private void addMethodConflicts(MultiMap<PsiElement, String> conflicts) {
    try {
      GrMethod prototype;
      final PsiMethod method = myChangeInfo.getMethod();
      if (!(method instanceof GrMethod)) return;

      PsiManager manager = method.getManager();
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(manager.getProject());
      final CanonicalTypes.Type returnType = myChangeInfo.getNewReturnType();
      String newMethodName = myChangeInfo.getNewName();
      if (method.isConstructor()) {
        prototype = factory.createConstructorFromText("foo", ArrayUtilRt.EMPTY_STRING_ARRAY, ArrayUtilRt.EMPTY_STRING_ARRAY, "{}", method);
      }
      else {
        prototype = factory.createMethodFromText("", "foo", returnType != null ? returnType.getTypeText() : null,
                                                 ArrayUtilRt.EMPTY_STRING_ARRAY, method);
      }
      prototype.setName(newMethodName);

      JavaParameterInfo[] parameters = myChangeInfo.getNewParameters();

      for (JavaParameterInfo info : parameters) {
        GrParameter param;
        if (info instanceof GrParameterInfo) {
          param = factory.createParameter(info.getName(), info.getTypeText(), ((GrParameterInfo)info).getDefaultInitializer(), (GroovyPsiElement)method);
        }
        else {
          param = factory.createParameter(info.getName(), info.getTypeText(), (GroovyPsiElement)method);
        }
        prototype.getParameterList().add(param);
      }

      ConflictsUtil.checkMethodConflicts(method.getContainingClass(), method, prototype, conflicts);
      GrMethodConflictUtil.checkMethodConflicts(method.getContainingClass(), prototype, ((GrMethod)method), conflicts, true);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
