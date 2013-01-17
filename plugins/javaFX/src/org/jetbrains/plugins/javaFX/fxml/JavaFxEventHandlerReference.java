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
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodQuickFix;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 1/16/13
 */
public class JavaFxEventHandlerReference extends PsiReferenceBase<XmlAttributeValue> {
  private PsiMethod myEventHandler;
  private final PsiClass myController;

  public JavaFxEventHandlerReference(XmlAttributeValue element, final PsiMethod method, PsiClass controller) {
    super(element);
    myEventHandler = method;
    myController = controller;
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    return myEventHandler;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    if (myController == null) return EMPTY_ARRAY;
    final List<PsiMethod> availableHandlers = new ArrayList<PsiMethod>();
    for (PsiMethod psiMethod : myController.getMethods()) {
       if (isHandlerMethod(psiMethod)) {
         availableHandlers.add(psiMethod);
       }
    }
    return availableHandlers.isEmpty() ? EMPTY_ARRAY : ArrayUtil.toObjectArray(availableHandlers);
  }

  public static boolean isHandlerMethod(PsiMethod psiMethod) {
    if (!psiMethod.hasModifierProperty(PsiModifier.STATIC) && psiMethod.hasModifierProperty(PsiModifier.PUBLIC) && PsiType.VOID.equals(psiMethod.getReturnType())) {
      final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
      if (parameters.length == 1) {
        final PsiType parameterType = parameters[0].getType();
        if (parameterType.equalsToText(FxmlConstants.JAVAFX_EVENT)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public TextRange getRangeInElement() {
    final TextRange range = super.getRangeInElement();
    return new TextRange(range.getStartOffset() + 1, range.getEndOffset());
  }

  public static class JavaFxUnresolvedReferenceHandlerQuickfixProvider extends UnresolvedReferenceQuickFixProvider<JavaFxEventHandlerReference> {

    @Override
    public void registerFixes(final JavaFxEventHandlerReference ref, final QuickFixActionRegistrar registrar) {
      if (ref.myController != null && ref.myEventHandler == null) {
        final CreateMethodQuickFix quickFix = CreateMethodQuickFix.createFix(ref.myController, getHandlerSignature(ref), "");
        if (quickFix != null) {
          registrar.register(quickFix);
        }
      }
    }

    private static String getHandlerSignature(JavaFxEventHandlerReference ref) {
      return "public void " + ref.getElement().getValue().substring(1) + "(" + FxmlConstants.JAVAFX_EVENT + " e)";
    }

    @NotNull
    @Override
    public Class<JavaFxEventHandlerReference> getReferenceClass() {
      return JavaFxEventHandlerReference.class;
    }
  }
}
