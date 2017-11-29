package org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightFixUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureDialog;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxEventHandlerInspection extends XmlSuppressableInspectionTool {
  public boolean myDetectNonVoidReturnType;

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    if (!JavaFxFileTypeFactory.isFxml(session.getFile())) return PsiElementVisitor.EMPTY_VISITOR;

    return new XmlElementVisitor() {
      @Override
      public void visitXmlAttributeValue(XmlAttributeValue xmlAttributeValue) {
        super.visitXmlAttributeValue(xmlAttributeValue);
        final PsiElement valueParent = xmlAttributeValue.getParent();
        if (!(valueParent instanceof XmlAttribute)) return;
        final XmlAttribute attribute = (XmlAttribute)valueParent;

        final List<PsiMethod> eventHandlerMethods = getEventHandlerMethods(attribute);
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

        final PsiClassType declaredType = JavaFxPsiUtil.getDeclaredEventType(attribute);
        if (declaredType == null) return;

        for (PsiMethod method : eventHandlerMethods) {
          final PsiParameter[] parameters = method.getParameterList().getParameters();
          if (parameters.length == 1) {
            final PsiType actualType = parameters[0].getType();
            if (actualType instanceof PsiClassType) {
              if (!actualType.isAssignableFrom(declaredType)) {
                final LocalQuickFix parameterTypeFix = new ChangeParameterTypeQuickFix(attribute, method, declaredType);
                final PsiClassType actualRawType = ((PsiClassType)actualType).rawType();
                final PsiClassType expectedRawType = declaredType.rawType();
                if (actualRawType.isAssignableFrom(expectedRawType)) {
                  final List<LocalQuickFix> quickFixes = new ArrayList<>();
                  quickFixes.add(parameterTypeFix);
                  collectFieldTypeFixes(attribute, (PsiClassType)actualType, quickFixes);
                  holder.registerProblem(xmlAttributeValue,
                                         "Incompatible generic parameter of event handler argument: " + actualType.getCanonicalText() +
                                         " is not assignable from " + declaredType.getCanonicalText(),
                                         quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
                }
                else {
                  holder.registerProblem(xmlAttributeValue,
                                         "Incompatible event handler argument: " + actualRawType.getCanonicalText() +
                                         " is not assignable from " + expectedRawType.getCanonicalText(),
                                         parameterTypeFix);
                }
              }
            }
          }
        }
      }
    };
  }

  @NotNull
  private static List<PsiMethod> getEventHandlerMethods(@NotNull XmlAttribute attribute) {
    final PsiClass controllerClass = JavaFxPsiUtil.getControllerClass(attribute.getContainingFile());
    if (controllerClass == null) return Collections.emptyList();

    final String valueText = attribute.getValue();
    if (valueText == null || !valueText.startsWith("#")) return Collections.emptyList();
    final String eventHandlerMethodName = valueText.substring(1);

    return Arrays.stream(controllerClass.findMethodsByName(eventHandlerMethodName, true))
      .filter(method -> !method.hasModifierProperty(PsiModifier.STATIC) && JavaFxPsiUtil.isVisibleInFxml(method))
      .filter(JavaFxEventHandlerInspection::hasEventArgument)
      .collect(Collectors.toList());
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

  private static void collectFieldTypeFixes(@NotNull XmlAttribute attribute,
                                            @NotNull PsiClassType eventType,
                                            @NotNull List<LocalQuickFix> quickFixes) {
    final XmlTag xmlTag = attribute.getParent();
    if (xmlTag == null) return;
    final XmlAttribute idAttribute = xmlTag.getAttribute(FxmlConstants.FX_ID);
    if (idAttribute == null) return;
    final XmlAttributeValue idValue = idAttribute.getValueElement();
    if (idValue == null) return;
    final PsiReference reference = idValue.getReference();
    if (reference == null) return;
    final PsiElement element = reference.resolve();
    if (!(element instanceof PsiField)) return;
    final PsiField tagField = (PsiField)element;
    if (tagField.hasModifierProperty(PsiModifier.STATIC) || !JavaFxPsiUtil.isVisibleInFxml(tagField)) return;

    final PsiType tagFieldType = tagField.getType();
    if (!(tagFieldType instanceof PsiClassType)) return;
    final PsiClassType rawFieldType = ((PsiClassType)tagFieldType).rawType();

    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(eventType);
    final PsiClass eventClass = resolveResult.getElement();
    if (eventClass == null) return;

    final PsiSubstitutor eventSubstitutor = resolveResult.getSubstitutor();
    for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(eventClass)) {
      final PsiType eventTypeArgument = eventSubstitutor.substitute(typeParameter);
      final PsiClassType rawEventArgument = eventTypeArgument instanceof PsiClassType ? ((PsiClassType)eventTypeArgument).rawType() : null;
      if (rawFieldType.equals(rawEventArgument)) {
        final List<IntentionAction> fixes = HighlightFixUtil.getChangeVariableTypeFixes(tagField, eventTypeArgument);
        for (IntentionAction action : fixes) {
          if (action instanceof LocalQuickFix) {
            quickFixes.add((LocalQuickFix)action);
          }
        }
        break;
      }
    }
  }

  private static class ChangeParameterTypeQuickFix extends LocalQuickFixOnPsiElement {
    final String myText;

    public ChangeParameterTypeQuickFix(@NotNull XmlAttribute attribute, @NotNull PsiMethod method,
                                       @NotNull PsiType suggestedParameterType) {
      super(attribute);
      myText = "Change parameter type of '" + JavaHighlightUtil.formatMethod(method) +
               "' to " + suggestedParameterType.getPresentableText();
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @NotNull
    @Override
    public String getText() {
      return myText;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Change parameter type of event handler method";
    }

    @Override
    public boolean isAvailable(@NotNull Project project,
                               @NotNull PsiFile file,
                               @NotNull PsiElement startElement,
                               @NotNull PsiElement endElement) {
      return startElement instanceof XmlAttribute;
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
      if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
      if (!(startElement instanceof XmlAttribute)) return;
      final XmlAttribute attribute = (XmlAttribute)startElement;

      final List<PsiMethod> eventHandlerMethods = getEventHandlerMethods(attribute);
      if (eventHandlerMethods.size() != 1) return;
      final PsiMethod method = eventHandlerMethods.get(0);
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length != 1) return;
      final String parameterName = parameters[0].getName();
      final PsiClassType declaredType = JavaFxPsiUtil.getDeclaredEventType(attribute);
      if (declaredType == null) return;

      final ParameterInfoImpl parameterInfo = new ParameterInfoImpl(0, parameterName, declaredType);
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        final ChangeSignatureProcessor processor =
          new ChangeSignatureProcessor(project, method, false, null, method.getName(), method.getReturnType(),
                                       new ParameterInfoImpl[]{parameterInfo});
        processor.run();
      }
      else {
        final List<ParameterInfoImpl> parameterInfos = Collections.singletonList(parameterInfo);
        final JavaChangeSignatureDialog dialog =
          JavaChangeSignatureDialog.createAndPreselectNew(project, method, parameterInfos, false, null);
        dialog.setParameterInfos(parameterInfos);
        dialog.show();
      }
    }
  }
}
