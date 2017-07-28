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
import com.intellij.codeInsight.intention.JvmCommonIntentionActionsFactory;
import com.intellij.codeInsight.intention.MethodInsertionInfo;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JavaFxEventHandlerReference extends PsiReferenceBase<XmlAttributeValue> {
  private final PsiMethod myEventHandler;
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
  public TextRange getRangeInElement() {
    final TextRange range = super.getRangeInElement();
    return new TextRange(range.getStartOffset() + 1, range.getEndOffset());
  }

  public static class JavaFxUnresolvedReferenceHandlerQuickfixProvider
    extends UnresolvedReferenceQuickFixProvider<JavaFxEventHandlerReference> {

    @Override
    public void registerFixes(@NotNull final JavaFxEventHandlerReference ref, @NotNull final QuickFixActionRegistrar registrar) {
      if (ref.myController != null && ref.myEventHandler == null) {
        JvmCommonIntentionActionsFactory intentionActionsFactory =
          JvmCommonIntentionActionsFactory.forLanguage(ref.myController.getLanguage());
        if (intentionActionsFactory == null) return;

        String javaSignature = getHandlerSignature(ref);
        PsiMethod javaMethod = JavaPsiFacade.getElementFactory(ref.myController.getProject())
          .createMethodFromText(javaSignature, ref.myController);

        MethodInsertionInfo.Method method =
          MethodInsertionInfo.simpleMethodInfo(ref.myController,
                                               javaMethod.getName(),
                                               Arrays.asList(javaMethod.getModifiers()),
                                               javaMethod.getReturnType(),
                                               Arrays.asList(javaMethod.getParameterList().getParameters()));
        intentionActionsFactory.createAddCallableMemberActions(method).forEach(registrar::register);
      }
    }

    private static String getHandlerSignature(JavaFxEventHandlerReference ref) {
      final XmlAttributeValue element = ref.getElement();
      PsiType eventType = getEventType(element);
      final String modifiers = getModifiers(element.getProject());
      return modifiers + " void " + element.getValue().substring(1) + "(" +
             eventType.getCanonicalText() + " " + suggestParamName(element.getProject(), eventType) +
             ")";
    }

    private static String suggestParamName(Project project, PsiType eventType) {
      SuggestedNameInfo suggestedInfo = JavaCodeStyleManager.getInstance(project)
        .suggestVariableName(VariableKind.PARAMETER, null, null, eventType);
      if (suggestedInfo.names.length < 1) {
        return "e";
      }
      return suggestedInfo.names[0];
    }

    @NotNull
    private static PsiType getEventType(XmlAttributeValue element) {
      final PsiElement parent = element.getParent();
      if (parent instanceof XmlAttribute) {
        final PsiClassType eventType = JavaFxPsiUtil.getDeclaredEventType((XmlAttribute)parent);
        if (eventType != null) {
          return eventType;
        }
      }
      return PsiType.getTypeByName(JavaFxCommonNames.JAVAFX_EVENT, element.getProject(), element.getResolveScope());
    }

    @NotNull
    private static String getModifiers(@NotNull Project project) {
      String visibility = CodeStyleSettingsManager.getSettings(project).VISIBILITY;
      if (VisibilityUtil.ESCALATE_VISIBILITY.equals(visibility)) visibility = PsiModifier.PRIVATE;
      final boolean needAnnotation = !PsiModifier.PUBLIC.equals(visibility);
      final String modifier = !PsiModifier.PACKAGE_LOCAL.equals(visibility) ? visibility : "";
      return needAnnotation ? "@" + JavaFxCommonNames.JAVAFX_FXML_ANNOTATION + " " + modifier : modifier;
    }

    @NotNull
    @Override
    public Class<JavaFxEventHandlerReference> getReferenceClass() {
      return JavaFxEventHandlerReference.class;
    }
  }
}
