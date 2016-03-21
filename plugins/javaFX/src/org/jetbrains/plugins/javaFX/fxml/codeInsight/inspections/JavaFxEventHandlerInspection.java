package org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.IntentionWrapper;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil.getTagClass;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxEventHandlerInspection extends XmlSuppressableInspectionTool {
  public boolean myDetectNonVoidReturnType;

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override
      public void visitXmlFile(XmlFile file) {
        if (!JavaFxFileTypeFactory.isFxml(file)) return;
        super.visitXmlFile(file);
      }

      @Override
      public void visitXmlAttributeValue(XmlAttributeValue xmlAttributeValue) {
        super.visitXmlAttributeValue(xmlAttributeValue);
        final PsiElement valueParent = xmlAttributeValue.getParent();
        if (!(valueParent instanceof XmlAttribute)) return;
        final XmlAttribute attribute = (XmlAttribute)valueParent;
        final PsiClass controllerClass = JavaFxPsiUtil.getControllerClass(attribute.getContainingFile());
        if (controllerClass == null) return;

        final String valueText = attribute.getValue();
        if (valueText == null || !valueText.startsWith("#")) return;
        final String eventHandlerMethodName = valueText.substring(1);

        List<PsiMethod> eventHandlerMethods =
          Arrays.stream(controllerClass.findMethodsByName(eventHandlerMethodName, true))
            .filter(method -> !method.hasModifierProperty(PsiModifier.STATIC) && JavaFxPsiUtil.isVisibleInFxml(method))
            .filter(JavaFxEventHandlerInspection::hasEventArgument)
            .collect(Collectors.toList());
        if (eventHandlerMethods.size() == 0) return;
        if (eventHandlerMethods.size() != 1) {
          holder.registerProblem(xmlAttributeValue, "Ambiguous event handler name: more than one matching method found");
        }

        if (myDetectNonVoidReturnType) {
          eventHandlerMethods.stream()
            .map(PsiMethod::getReturnType)
            .filter(returnType -> !PsiType.VOID.equals(returnType))
            .findAny()
            .ifPresent(ignored -> holder.registerProblem(xmlAttributeValue, "Return type of event handler should be void"));
        }

        final PsiSubstitutor tagClassSubstitutor = JavaFxPsiUtil.getTagClassSubstitutor(attribute, controllerClass);
        final PsiClassType declaredType = getDeclaredEventType(attribute, tagClassSubstitutor);
        if (declaredType == null) return;

        for (PsiMethod method : eventHandlerMethods) {
          final PsiParameter[] parameters = method.getParameterList().getParameters();
          if (parameters.length == 1) {
            final PsiType actualType = parameters[0].getType();
            if (actualType instanceof PsiClassType) {
              if (!actualType.isAssignableFrom(declaredType)) {
                final LocalQuickFix quickFix = createChangeArgumentTypeQuickFix(controllerClass, declaredType, method, tagClassSubstitutor);

                final PsiClassType actualRawType = ((PsiClassType)actualType).rawType();
                final PsiClassType expectedRawType = declaredType.rawType();
                if (actualRawType.isAssignableFrom(expectedRawType)) {
                  holder.registerProblem(xmlAttributeValue,
                                         "Incompatible generic parameter of event handler argument: " + actualType.getCanonicalText() +
                                         " is not assignable from " + declaredType.getCanonicalText(),
                                         quickFix);
                }
                else {
                  holder.registerProblem(xmlAttributeValue,
                                         "Incompatible event handler argument: " + actualRawType.getCanonicalText() +
                                         " is not assignable from " + expectedRawType.getCanonicalText(),
                                         quickFix);
                }
              }
            }
          }
        }
      }
    };
  }

  @NotNull
  private static LocalQuickFix createChangeArgumentTypeQuickFix(@NotNull PsiClass controllerClass,
                                                                @NotNull PsiClassType declaredType,
                                                                @NotNull PsiMethod method,
                                                                @Nullable PsiSubstitutor tagClassSubstitutor) {
    final PsiElementFactory psiElementFactory = JavaPsiFacade.getElementFactory(controllerClass.getProject());
    final PsiExpression expression = psiElementFactory.createExpressionFromText("new " + declaredType.getCanonicalText() + "(){}", method);
    final IntentionAction intentionAction = QuickFixFactory.getInstance()
      .createChangeMethodSignatureFromUsageFix(method, new PsiExpression[]{expression},
                                               tagClassSubstitutor != null ? tagClassSubstitutor : PsiSubstitutor.EMPTY,
                                               controllerClass, false, 2);
    return new IntentionWrapper(intentionAction, method.getContainingFile());
  }

  @Nullable
  private static PsiClassType getDeclaredEventType(@NotNull XmlAttribute attribute, @Nullable PsiSubstitutor tagClassSubstitutor) {
    final PsiClass tagClass = getTagClass(attribute.getParent());
    if (tagClass == null) return null;

    final PsiType eventHandlerPropertyType = JavaFxPsiUtil.getEventHandlerPropertyType(tagClass, attribute.getName());
    if (eventHandlerPropertyType == null) return null;

    final PsiType handlerType =
      tagClassSubstitutor != null ? tagClassSubstitutor.substitute(eventHandlerPropertyType) : eventHandlerPropertyType;
    if (handlerType instanceof PsiClassType) {
      final PsiType eventType = JavaFxPsiUtil.substituteEventType((PsiClassType)handlerType, tagClass.getProject());
      if (eventType instanceof PsiClassType) {
        return (PsiClassType)eventType;
      }
      if (eventType instanceof PsiWildcardType) {
        PsiWildcardType wildcardType = (PsiWildcardType)eventType;
        if (wildcardType.isSuper()) {
          final PsiType bound = wildcardType.getBound();
          if (bound instanceof PsiClassType) return (PsiClassType)bound;
        }
      }
    }
    return null;
  }

  private static boolean hasEventArgument(@NotNull PsiMethod method) {
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    return parameters.length == 0 ||
           parameters.length == 1 && InheritanceUtil.isInheritor(parameters[0].getType(), JavaFxCommonNames.JAVAFX_EVENT);
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Detect event handler method having non-void return type", this, "myDetectNonVoidReturnType");
  }
}
