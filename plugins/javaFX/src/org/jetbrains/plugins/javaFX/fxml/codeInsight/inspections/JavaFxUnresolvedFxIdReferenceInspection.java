// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypeInfoImpl;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFieldFromUsageFix;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFieldFromUsageHelper;
import com.intellij.codeInspection.*;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.actions.*;
import com.intellij.lang.jvm.util.JvmUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.VisibilityUtil;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.JavaFXBundle;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxBuiltInTagDescriptor;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxClassTagDescriptorBase;
import org.jetbrains.plugins.javaFX.fxml.refs.JavaFxFieldIdReferenceProvider;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class JavaFxUnresolvedFxIdReferenceInspection extends XmlSuppressableInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder,
                                        final boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    if (!JavaFxFileTypeFactory.isFxml(session.getFile())) return PsiElementVisitor.EMPTY_VISITOR;

    return new XmlElementVisitor() {
      @Override
      public void visitXmlAttribute(@NotNull XmlAttribute attribute) {
        super.visitXmlAttribute(attribute);
        if (FxmlConstants.FX_ID.equals(attribute.getName())) {
          final XmlAttributeValue valueElement = attribute.getValueElement();
          if (valueElement != null && valueElement.getTextLength() > 0) {
            final PsiClass controllerClass = JavaFxPsiUtil.getControllerClass(attribute.getContainingFile());
            if (controllerClass != null) {
              final PsiReference reference = valueElement.getReference();
              if (reference instanceof JavaFxFieldIdReferenceProvider.JavaFxControllerFieldRef && ((JavaFxFieldIdReferenceProvider.JavaFxControllerFieldRef)reference).isUnresolved()) {
                final PsiClass fieldClass =
                  checkContext(((JavaFxFieldIdReferenceProvider.JavaFxControllerFieldRef)reference).getXmlAttributeValue());
                if (fieldClass != null) {
                  final String text = reference.getCanonicalText();
                  boolean validName = LanguageNamesValidation.isIdentifier(fieldClass.getLanguage(), text, fieldClass.getProject());
                  holder.registerProblem(reference.getElement(), reference.getRangeInElement(), JavaFXBundle.message("inspection.javafx.unresolved.fx.id.reference.problem"),
                                         isOnTheFly && validName ?
                                         createFixes((JavaFxFieldIdReferenceProvider.JavaFxControllerFieldRef)reference, holder.getFile()) : LocalQuickFix.EMPTY_ARRAY);
                }
              }
            }
          }
        }
      }
    };
  }

  private static LocalQuickFix @NotNull [] createFixes(JavaFxFieldIdReferenceProvider.JavaFxControllerFieldRef reference, PsiFile file) {
    
    @PsiModifier.ModifierConstant
    String visibility = JavaCodeStyleSettings.getInstance(file).VISIBILITY;
   
    Collection<AnnotationRequest> annotations; 
    if (!PsiModifier.PUBLIC.equals(visibility)) {
      annotations = Collections.singletonList(AnnotationRequestsKt.annotationRequest(JavaFxCommonNames.JAVAFX_FXML_ANNOTATION));
    }
    else {
      annotations = Collections.emptyList();
    }

    JvmModifier modifier = JvmUtil.getAccessModifier(VisibilityUtil.getAccessLevel(visibility));
    List<ExpectedType> expectedTypes = ExpectedTypesKt.expectedTypes(JavaPsiFacade.getElementFactory(file.getProject()).createType(checkContext(reference.getXmlAttributeValue())), ExpectedType.Kind.SUBTYPE);
    CreateFieldRequest request = FieldRequestsKt.fieldRequest(reference.getCanonicalText(), 
                                                              annotations, 
                                                              Collections.singletonList(modifier),
                                                              expectedTypes,
                                                              new PsiJvmSubstitutor(file.getProject(), PsiSubstitutor.EMPTY), null, false);
    return IntentionWrapper.wrapToQuickFixes(JvmElementActionFactories.createAddFieldActions(reference.getAClass(), request), file).toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  protected static PsiClass checkContext(final XmlAttributeValue attributeValue) {
    if (attributeValue == null) return null;
    final PsiElement parent = attributeValue.getParent();
    if (parent instanceof XmlAttribute) {
      return checkClass(((XmlAttribute)parent).getParent());
    }
    return null;
  }

  private static PsiClass checkClass(XmlTag tag) {
    if (tag != null) {
      final XmlElementDescriptor descriptor = tag.getDescriptor();
      if (descriptor instanceof JavaFxClassTagDescriptorBase) {
        final PsiElement declaration = descriptor.getDeclaration();
        if (declaration instanceof PsiClass) {
          return (PsiClass)declaration;
        }
      } else if (descriptor instanceof JavaFxBuiltInTagDescriptor) {
        final XmlTag includedRoot = JavaFxBuiltInTagDescriptor.getIncludedRoot(tag);
        if (includedRoot != null && !includedRoot.equals(tag)) {
          return checkClass(includedRoot);
        }
      }
    }
    return null;
  }

  private static final class CreateFieldFromUsageQuickFix implements LocalQuickFix {
    private final String myCanonicalName;

    private CreateFieldFromUsageQuickFix(String canonicalName) {
      myCanonicalName = canonicalName;
    }

    @NotNull
    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.create.title.x", JavaElementKind.FIELD.object(), myCanonicalName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return QuickFixBundle.message("create.field.from.usage.family");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      final XmlAttributeValue attrValue = PsiTreeUtil.getParentOfType(psiElement, XmlAttributeValue.class, false);
      assert attrValue != null;

      final JavaFxFieldIdReferenceProvider.JavaFxControllerFieldRef reference =
        (JavaFxFieldIdReferenceProvider.JavaFxControllerFieldRef)attrValue.getReference();
      assert reference != null;

      final PsiClass targetClass = reference.getAClass();
      if (!FileModificationService.getInstance().prepareFileForWrite(targetClass.getContainingFile())) {
        return;
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiField field = factory.createField(reference.getCanonicalText(), PsiTypes.intType());
      final PsiModifierList modifierList = field.getModifierList();
      if (modifierList != null) {
        @PsiModifier.ModifierConstant
        String visibility = JavaCodeStyleSettings.getInstance(targetClass.getContainingFile()).VISIBILITY;
        if (VisibilityUtil.ESCALATE_VISIBILITY.equals(visibility)) visibility = PsiModifier.PRIVATE;
        VisibilityUtil.setVisibility(modifierList, visibility);
        if (!PsiModifier.PUBLIC.equals(visibility)) {
          modifierList.addAnnotation(JavaFxCommonNames.JAVAFX_FXML_ANNOTATION);
        }
      }

      field = CreateFieldFromUsageHelper.insertField(targetClass, field, psiElement);

      final PsiClassType fieldType = factory.createType(checkContext(reference.getXmlAttributeValue()));
      final ExpectedTypeInfo[] types = {new ExpectedTypeInfoImpl(fieldType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, fieldType, TailType.NONE,
                                                                 null, ExpectedTypeInfoImpl.NULL)};
      CreateFieldFromUsageFix.createFieldFromUsageTemplate(targetClass, project, types, field, false, psiElement);
    }
  }
}
