// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public class SimplifiableAnnotationInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    if (infos.length == 0) {
      return InspectionGadgetsBundle.message("simplifiable.annotation.whitespace.problem.descriptor");
    }
    else if (infos[0] instanceof PsiArrayInitializerMemberValue arrayValue) {
      return InspectionGadgetsBundle.message("simplifiable.annotation.braces.problem.descriptor", arrayValue.getText());
    }
    else {
      return InspectionGadgetsBundle.message("simplifiable.annotation.problem.descriptor");
    }
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new SimplifiableAnnotationFix();
  }

  private static class SimplifiableAnnotationFix extends InspectionGadgetsFix {

    SimplifiableAnnotationFix() {}

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("simplifiable.annotation.quickfix");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiAnnotation annotation = PsiTreeUtil.getParentOfType(element, PsiAnnotation.class);
      if (annotation == null) {
        return;
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      CommentTracker tracker = new CommentTracker();
      final String annotationText = buildAnnotationText(annotation, tracker);
      final PsiAnnotation newAnnotation = factory.createAnnotationFromText(annotationText, element);
      tracker.replaceAndRestoreComments(annotation, newAnnotation);
    }

    private static String buildAnnotationText(PsiAnnotation annotation, CommentTracker tracker) {
      final StringBuilder out = new StringBuilder("@");
      final PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
      assert nameReferenceElement != null;
      out.append(tracker.text(nameReferenceElement));
      final PsiAnnotationParameterList parameterList = annotation.getParameterList();
      final PsiNameValuePair[] attributes = parameterList.getAttributes();
      if (attributes.length == 0) {
        return out.toString();
      }
      out.append('(');
      if (attributes.length == 1) {
        final PsiNameValuePair attribute = attributes[0];
        @NonNls final String name = attribute.getName();
        if (name != null && !PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(name)) {
          out.append(name).append('=');
        }
        buildAttributeValueText(attribute.getValue(), out, tracker);
      }
      else {
        for (int i = 0; i < attributes.length; i++) {
          final PsiNameValuePair attribute = attributes[i];
          if (i > 0) {
            out.append(',');
          }
          out.append(attribute.getName()).append('=');
          buildAttributeValueText(attribute.getValue(), out, tracker);
        }
      }
      out.append(')');
      return out.toString();
    }

    private static void buildAttributeValueText(PsiAnnotationMemberValue value,
                                                StringBuilder out,
                                                CommentTracker tracker) {
      if (value instanceof PsiArrayInitializerMemberValue arrayValue) {
        final PsiAnnotationMemberValue[] initializers = arrayValue.getInitializers();
        if (initializers.length == 1) {
          out.append(tracker.text(initializers[0]));
          return;
        }
      }
      else if (value instanceof PsiAnnotation) {
        out.append(buildAnnotationText((PsiAnnotation)value, tracker));
        return;
      }
      out.append(tracker.text(value));
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SimplifiableAnnotationVisitor();
  }

  private static class SimplifiableAnnotationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAnnotation(@NotNull PsiAnnotation annotation) {
      super.visitAnnotation(annotation);
      final PsiAnnotationParameterList parameterList = annotation.getParameterList();
      final PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
      if (nameReferenceElement == null) {
        return;
      }
      final PsiElement[] annotationChildren = annotation.getChildren();
      if (annotationChildren.length >= 2 && annotationChildren[1] instanceof PsiWhiteSpace && !containsError(annotation)) {
        registerError(annotationChildren[1]);
        if (!isOnTheFly())  return;
      }
      if (annotationChildren.length >= 4) {
        PsiElement child = annotationChildren[annotationChildren.length - 2];
        if (child instanceof PsiWhiteSpace && !containsError(annotation)) {
          registerError(child);
          if (!isOnTheFly())  return;
        }
      }
      final PsiNameValuePair[] attributes = parameterList.getAttributes();
      if (attributes.length == 0) {
        if (parameterList.getFirstChild() != null && !containsError(annotation)) {
          registerError(parameterList, ProblemHighlightType.LIKE_UNUSED_SYMBOL, parameterList);
        }
      }
      else if (attributes.length == 1) {
        final PsiNameValuePair attribute = attributes[0];
        final PsiIdentifier identifier = attribute.getNameIdentifier();
        final PsiAnnotationMemberValue attributeValue = attribute.getValue();
        if (identifier != null && attributeValue != null) {
          @NonNls final String name = attribute.getName();
          if (PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(name) && !containsError(annotation)) {
            registerErrorAtOffset(attribute, 0, attributeValue.getStartOffsetInParent(),
                                  ProblemHighlightType.LIKE_UNUSED_SYMBOL, attribute);
            if (!isOnTheFly())  return;
          }
        }
        if (!(attributeValue instanceof PsiArrayInitializerMemberValue arrayValue)) {
          return;
        }
        final PsiAnnotationMemberValue[] initializers = arrayValue.getInitializers();
        if (initializers.length != 1) {
          return;
        }
        if (!containsError(annotation)) {
          registerError(arrayValue.getFirstChild(), ProblemHighlightType.LIKE_UNUSED_SYMBOL, arrayValue);
          if (!isOnTheFly())  return;
          registerError(arrayValue.getLastChild(), ProblemHighlightType.LIKE_UNUSED_SYMBOL, arrayValue);
        }
      }
      else {
        for (PsiNameValuePair attribute : attributes) {
          final PsiAnnotationMemberValue value = attribute.getValue();
          if (!(value instanceof PsiArrayInitializerMemberValue arrayValue)) {
            continue;
          }
          final PsiAnnotationMemberValue[] initializers = arrayValue.getInitializers();
          if (initializers.length != 1) {
            continue;
          }
          if (!containsError(annotation)) {
            registerError(arrayValue.getFirstChild(), ProblemHighlightType.LIKE_UNUSED_SYMBOL, arrayValue);
            if (!isOnTheFly())  return;
            registerError(arrayValue.getLastChild(), ProblemHighlightType.LIKE_UNUSED_SYMBOL, arrayValue);
          }
        }
      }
    }

    private static boolean containsError(PsiAnnotation annotation) {
      final PsiClass aClass = annotation.resolveAnnotationType();
      if (aClass == null) {
        return true;
      }
      final Set<String> names = new HashSet<>();
      final PsiAnnotationParameterList annotationParameterList = annotation.getParameterList();
      if (PsiUtilCore.hasErrorElementChild(annotationParameterList)) {
        return true;
      }
      final PsiNameValuePair[] attributes = annotationParameterList.getAttributes();
      for (PsiNameValuePair attribute : attributes) {
        final PsiReference reference = attribute.getReference();
        if (reference == null) {
          return true;
        }
        final PsiMethod method = (PsiMethod)reference.resolve();
        if (method == null) {
          return true;
        }
        final PsiAnnotationMemberValue value = attribute.getValue();
        if (value == null || PsiUtilCore.hasErrorElementChild(value)) {
          return true;
        }
        if (value instanceof PsiAnnotation && containsError((PsiAnnotation)value)) {
          return true;
        }
        if (!hasCorrectType(value, method.getReturnType())) {
          return true;
        }
        final String name = attribute.getName();
        if (!names.add(name != null ? name : PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)) {
          return true;
        }
      }

      for (PsiMethod method : aClass.getMethods()) {
        if (!(method instanceof PsiAnnotationMethod annotationMethod)) {
          continue;
        }
        if (annotationMethod.getDefaultValue() == null && !names.contains(annotationMethod.getName())) {
          return true; // missing a required argument
        }
      }
      return false;
    }

    private static boolean hasCorrectType(@Nullable PsiAnnotationMemberValue value, PsiType expectedType) {
      if (value == null) return false;

      if (expectedType instanceof PsiClassType &&
          expectedType.equalsToText(CommonClassNames.JAVA_LANG_CLASS) &&
          !(value instanceof PsiClassObjectAccessExpression)) {
        return false;
      }

      if (value instanceof PsiAnnotation) {
        final PsiJavaCodeReferenceElement nameRef = ((PsiAnnotation)value).getNameReferenceElement();
        if (nameRef == null) return true;

        if (expectedType instanceof PsiClassType) {
          final PsiClass aClass = ((PsiClassType)expectedType).resolve();
          if (aClass != null && nameRef.isReferenceTo(aClass)) return true;
        }

        if (expectedType instanceof PsiArrayType) {
          final PsiType componentType = ((PsiArrayType)expectedType).getComponentType();
          if (componentType instanceof PsiClassType) {
            final PsiClass aClass = ((PsiClassType)componentType).resolve();
            if (aClass != null && nameRef.isReferenceTo(aClass)) return true;
          }
        }
        return false;
      }
      if (value instanceof PsiArrayInitializerMemberValue) {
        return expectedType instanceof PsiArrayType;
      }
      if (value instanceof PsiExpression expression) {
        return expression.getType() != null && TypeConversionUtil.areTypesAssignmentCompatible(expectedType, expression) ||
               expectedType instanceof PsiArrayType &&
               TypeConversionUtil.areTypesAssignmentCompatible(((PsiArrayType)expectedType).getComponentType(), expression);
      }
      return true;
    }
  }
}
