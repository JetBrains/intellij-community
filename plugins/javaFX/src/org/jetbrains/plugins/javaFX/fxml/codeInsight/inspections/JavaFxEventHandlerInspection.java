package org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil.isNotFullyResolvedGeneric;

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
      public void visitXmlAttribute(XmlAttribute attribute) {
        super.visitXmlAttribute(attribute);

        final PsiFile containingFile = attribute.getContainingFile();
        final PsiClass controllerClass = JavaFxPsiUtil.getControllerClass(containingFile);
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
          holder.registerProblem(getHighlightedElement(attribute), "Ambiguous event handler name: more than one matching method found");
        }

        if (myDetectNonVoidReturnType) {
          eventHandlerMethods.stream()
            .map(PsiMethod::getReturnType)
            .filter(returnType -> !PsiType.VOID.equals(returnType))
            .findAny()
            .ifPresent(ignored -> holder.registerProblem(getHighlightedElement(attribute), "Return type of event handler should be void"));
        }

        final PsiClassType eventClassType = getHandlerArgumentClassType(attribute, controllerClass);
        if (eventClassType == null) return;
        final boolean eventClassTypeNotFullyResolved = isNotFullyResolvedGeneric(eventClassType);

        eventHandlerMethods.stream()
          .map(method -> method.getParameterList().getParameters())
          .filter(parameters -> parameters.length == 1)
          .map(parameters -> parameters[0].getType())
          .forEach(argType -> {
            if (argType instanceof PsiClassType) {
              final boolean assignable;
              if (eventClassTypeNotFullyResolved || isNotFullyResolvedGeneric((PsiClassType)argType)) {
                assignable = isAssignableFromIgnoringUnresolved((PsiClassType)argType, eventClassType);
              }
              else {
                assignable = argType.isAssignableFrom(eventClassType);
              }
              if (!assignable) {
                holder.registerProblem(getHighlightedElement(attribute),
                                       "Incompatible event handler argument: " + argType.getPresentableText() +
                                       " is not assignable from " + eventClassType.getPresentableText());
              }
            }
            else {
              holder.registerProblem(getHighlightedElement(attribute),
                                     "Unresolved event handler argument type " + argType.getCanonicalText());
            }
          });
      }
    };
  }

  private static boolean isAssignableFromIgnoringUnresolved(@Nullable PsiClassType left, @Nullable PsiClassType right) {
    if (left == null || right == null) return true;
    final PsiClassType.ClassResolveResult leftResolveResult = left.resolveGenerics();
    final PsiClassType.ClassResolveResult rightResolveResult = right.resolveGenerics();

    final PsiClass leftClass = leftResolveResult.getElement();
    final PsiClass rightClass = rightResolveResult.getElement();

    if (leftClass == null || rightClass == null || leftClass instanceof PsiTypeParameter || rightClass instanceof PsiTypeParameter) {
      return true;
    }
    if (leftClass.getManager().areElementsEquivalent(leftClass, rightClass)) {
      // generic args are assignable or unresolved
      if (!leftClass.hasTypeParameters()) return true;
      final PsiSubstitutor leftSubstitutor = leftResolveResult.getSubstitutor();
      final PsiSubstitutor rightSubstitutor = rightResolveResult.getSubstitutor();
      final PsiTypeParameter[] typeParameters = leftClass.getTypeParameters();
      for (PsiTypeParameter typeParameter : typeParameters) {
        final PsiType leftParameter = leftSubstitutor.substitute(typeParameter);
        if (leftParameter == null || leftParameter instanceof PsiTypeParameter) continue;
        final PsiType rightParameter = rightSubstitutor.substitute(typeParameter);
        if (rightParameter == null || rightParameter instanceof PsiTypeParameter) continue;
        final PsiClassType leftParameterClass = getBoundClassType(leftParameter, true);
        final PsiClassType rightParameterClass = getBoundClassType(rightParameter, false);
        if (!isAssignableFromIgnoringUnresolved(leftParameterClass, rightParameterClass)) {
          return false;
        }
      }
      return true;
    }
    else if (leftClass.isInheritor(rightClass, true)) {
      // generic args are equal
      final PsiType rightSubstitute = leftResolveResult.getSubstitutor().substitute(right);
      return rightSubstitute.isAssignableFrom(left);
    }
    return false;
  }

  @Nullable
  private static PsiClassType getBoundClassType(PsiType psiType, boolean isSuper) {
    if (psiType instanceof PsiClassType) {
      return (PsiClassType)psiType;
    }
    if (psiType instanceof PsiWildcardType) {
      PsiWildcardType wildcardType = (PsiWildcardType)psiType;
      if (isSuper && wildcardType.isSuper() || !isSuper && wildcardType.isExtends()) {
        final PsiType bound = wildcardType.getBound();
        if (bound instanceof PsiClassType) return (PsiClassType)bound;
      }
    }
    return null;
  }

  private static PsiElement getHighlightedElement(XmlAttribute attribute) {
    final XmlAttributeValue valueElement = attribute.getValueElement();
    return valueElement != null ? valueElement : attribute;
  }

  @Nullable
  private static PsiClassType getHandlerArgumentClassType(@NotNull XmlAttribute attribute, PsiClass controllerClass) {
    final XmlTag xmlTag = attribute.getParent();
    final XmlElementDescriptor descriptor = xmlTag.getDescriptor();
    if (descriptor == null) return null;
    final PsiElement declaration = descriptor.getDeclaration();
    if (!(declaration instanceof PsiClass)) return null;
    final PsiClass tagClass = (PsiClass)declaration;

    final String handlerSetterName = PropertyUtil.suggestSetterName(attribute.getName());
    final PsiMethod[] handlerSetterCandidates = tagClass.findMethodsByName(handlerSetterName, true);
    if (handlerSetterCandidates.length == 0) return null;

    final Project project = attribute.getProject();
    final PsiClass javaFxEventHandlerClass = JavaPsiFacade.getInstance(project).findClass(JavaFxCommonNames.JAVAFX_EVENT_EVENT_HANDLER,
                                                                                          GlobalSearchScope.allScope(project));
    if (javaFxEventHandlerClass != null) {
      final PsiTypeParameter[] typeParameters = javaFxEventHandlerClass.getTypeParameters();
      if (typeParameters.length == 1) {
        final PsiTypeParameter javaFxEventHandlerTypeParameter = typeParameters[0];
        final PsiSubstitutor tagClassSubstitutor = JavaFxPsiUtil.getTagClassSubstitutor(xmlTag, tagClass, controllerClass);
        for (PsiMethod handlerSetter : handlerSetterCandidates) {
          if (handlerSetter.hasModifierProperty(PsiModifier.STATIC) || !handlerSetter.hasModifierProperty(PsiModifier.PUBLIC)) continue;
          final PsiParameter[] parameters = handlerSetter.getParameterList().getParameters();
          if (parameters.length == 1) {
            PsiType handlerParameterType = parameters[0].getType();
            if (tagClassSubstitutor != null) {
              handlerParameterType = tagClassSubstitutor.substitute(handlerParameterType);
            }
            if (handlerParameterType instanceof PsiClassType &&
                InheritanceUtil.isInheritorOrSelf(((PsiClassType)handlerParameterType).resolve(), javaFxEventHandlerClass, true)) {
              final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)handlerParameterType).resolveGenerics();
              final PsiType eventType = resolveResult.getSubstitutor().substitute(javaFxEventHandlerTypeParameter);
              PsiClassType eventClassType = getBoundClassType(eventType, true);
              if (eventClassType != null) return eventClassType;
            }
          }
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
