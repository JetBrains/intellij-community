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

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodQuickFix;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonClassNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 1/16/13
 */
public class JavaFxEventHandlerReference extends PsiReferenceBase<XmlAttributeValue> {
  private final PsiClass myCurrentTagClass;
  private final PsiMethod myEventHandler;
  private final PsiClass myController;

  public JavaFxEventHandlerReference(XmlAttributeValue element, PsiClass currentTagClass, final PsiMethod method, PsiClass controller) {
    super(element);
    myCurrentTagClass = currentTagClass;
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
    if (!psiMethod.hasModifierProperty(PsiModifier.STATIC) && PsiType.VOID.equals(psiMethod.getReturnType())) {
      final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
      if (parameters.length == 1) {
        final PsiType parameterType = parameters[0].getType();
        if (InheritanceUtil.isInheritor(parameterType, JavaFxCommonClassNames.JAVAFX_EVENT)) {
          return true;
        }
      }
      return parameters.length == 0;
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
    public void registerFixes(@NotNull final JavaFxEventHandlerReference ref, @NotNull final QuickFixActionRegistrar registrar) {
      if (ref.myController != null && ref.myEventHandler == null) {
        final CreateMethodQuickFix quickFix = CreateMethodQuickFix.createFix(ref.myController, getHandlerSignature(ref), "");
        if (quickFix != null) {
          registrar.register(quickFix);
        }
      }
    }

    private static String getHandlerSignature(JavaFxEventHandlerReference ref) {
      final XmlAttributeValue element = ref.getElement();
      String canonicalText = JavaFxCommonClassNames.JAVAFX_EVENT;
      final PsiElement parent = element.getParent();
      if (parent instanceof XmlAttribute) {
        final XmlAttribute xmlAttribute = (XmlAttribute)parent;
        final Project project = element.getProject();
        final PsiField handlerField = ref.myCurrentTagClass.findFieldByName(xmlAttribute.getName(), true);
        if (handlerField != null) {
          final PsiClassType classType = JavaFxPsiUtil.getPropertyClassType(handlerField);
          if (classType != null) {
            final PsiClass eventHandlerClass = JavaPsiFacade.getInstance(project).findClass(JavaFxCommonClassNames.JAVAFX_EVENT_EVENT_HANDLER, GlobalSearchScope.allScope(project));
            final PsiTypeParameter[] typeParameters = eventHandlerClass != null ? eventHandlerClass.getTypeParameters() : null;
            if (typeParameters != null && typeParameters.length == 1) {
              final PsiTypeParameter typeParameter = typeParameters[0];
              final PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(eventHandlerClass, classType);
              final PsiType eventType = substitutor.substitute(typeParameter);
              if (eventType != null) {
                canonicalText = eventType.getCanonicalText();
              }
            }
          }
        }
      }
      return "public void " + element.getValue().substring(1) + "(" + canonicalText + " e)";
    }

    @NotNull
    @Override
    public Class<JavaFxEventHandlerReference> getReferenceClass() {
      return JavaFxEventHandlerReference.class;
    }
  }
}
