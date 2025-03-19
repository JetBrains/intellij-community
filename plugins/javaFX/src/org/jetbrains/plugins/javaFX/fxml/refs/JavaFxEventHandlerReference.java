// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.codeInsight.daemon.impl.analysis.PsiReferenceWithUnresolvedQuickFixes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import java.util.ArrayList;
import java.util.List;

public final class JavaFxEventHandlerReference extends PsiReferenceBase<XmlAttributeValue> implements PsiReferenceWithUnresolvedQuickFixes {
  final PsiMethod myEventHandler;
  final PsiClass myController;

  public JavaFxEventHandlerReference(XmlAttributeValue element, final PsiMethod method, PsiClass controller) {
    super(element);
    myEventHandler = method;
    myController = controller;
  }

  @Override
  public @Nullable PsiElement resolve() {
    return myEventHandler;
  }

  @Override
  public Object @NotNull [] getVariants() {
    if (myController == null) return EMPTY_ARRAY;
    final List<PsiMethod> availableHandlers = new ArrayList<>();
    for (PsiMethod psiMethod : myController.getAllMethods()) {
      if (isHandlerMethodSignature(psiMethod, myController) && JavaFxPsiUtil.isVisibleInFxml(psiMethod)) {
        availableHandlers.add(psiMethod);
      }
    }
    return availableHandlers.isEmpty() ? EMPTY_ARRAY : ArrayUtil.toObjectArray(availableHandlers);
  }

  public static boolean isHandlerMethodSignature(@NotNull PsiMethod psiMethod, @NotNull PsiClass controllerClass) {
    final PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) return false;
    if (!psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
      final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
      if (parameters.length == 1) {
        PsiType parameterType = parameters[0].getType();
        if (containingClass != null && !controllerClass.isEquivalentTo(containingClass)) {
          final PsiSubstitutor substitutor =
            TypeConversionUtil.getSuperClassSubstitutor(containingClass, controllerClass, PsiSubstitutor.EMPTY);
          parameterType = substitutor.substitute(parameterType);
        }
        if (InheritanceUtil.isInheritor(parameterType, JavaFxCommonNames.JAVAFX_EVENT)) {
          return true;
        }
      }
      return parameters.length == 0;
    }
    return false;
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    final TextRange range = super.getRangeInElement();
    return new TextRange(range.getStartOffset() + 1, range.getEndOffset());
  }
}
