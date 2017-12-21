// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.AddToIgnoreIfAnnotatedByListQuickFix;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class EmptyClassInspectionBase extends BaseInspection {
  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet ignorableAnnotations = new ExternalizableStringSet();
  @SuppressWarnings("PublicField")
  public boolean ignoreClassWithParameterization = false;
  @SuppressWarnings("PublicField")
  public boolean ignoreThrowables = true;
  @SuppressWarnings("PublicField")
  public boolean commentsAreContent = true;

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    defaultWriteSettings(node, "commentsAreContent");
    writeBooleanOption(node, "commentsAreContent", false);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("empty.class.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final Object element = infos[0];
    if (element instanceof PsiAnonymousClass) {
      return InspectionGadgetsBundle.message("empty.anonymous.class.problem.descriptor");
    }
    else if (element instanceof PsiClass) {
      return ((PsiClass)element).isEnum() ?
             InspectionGadgetsBundle.message("empty.enum.problem.descriptor"):
             InspectionGadgetsBundle.message("empty.class.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("empty.class.file.without.class.problem.descriptor");
    }
  }

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final Object info = infos[0];
    if (!(info instanceof PsiModifierListOwner)) {
      return InspectionGadgetsFix.EMPTY_ARRAY;
    }
    return AddToIgnoreIfAnnotatedByListQuickFix.build((PsiModifierListOwner)info, ignorableAnnotations);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EmptyClassVisitor();
  }

  private class EmptyClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitFile(PsiFile file) {
      super.visitFile(file);
      if (!(file instanceof PsiJavaFile)) {
        return;
      }
      final PsiJavaFile javaFile = (PsiJavaFile)file;
      if (javaFile.getClasses().length != 0) {
        return;
      }
      @NonNls final String fileName = javaFile.getName();
      if (PsiPackage.PACKAGE_INFO_FILE.equals(fileName) || PsiJavaModule.MODULE_INFO_FILE.equals(fileName)) {
        return;
      }
      registerError(file, file);
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      super.visitClass(aClass);
      if (FileTypeUtils.isInServerPageFile(aClass.getContainingFile())) {
        return;
      }
      if (aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT) && !aClass.isEnum()) {
        for (PsiClass superClass : aClass.getSupers()) {
          if (superClass.isInterface() || superClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
          }
        }
      }
      if (aClass instanceof PsiTypeParameter) {
        return;
      }
      final PsiMethod[] constructors = aClass.getConstructors();
      if (constructors.length > 0) {
        return;
      }
      final PsiMethod[] methods = aClass.getMethods();
      if (methods.length > 0) {
        return;
      }
      final PsiField[] fields = aClass.getFields();
      if (fields.length > 0) {
        return;
      }
      final PsiClassInitializer[] initializers = aClass.getInitializers();
      if (initializers.length > 0) {
        return;
      }
      if (commentsAreContent && PsiTreeUtil.getChildOfType(aClass, PsiComment.class) != null) {
        return;
      }
      if (ignoreClassWithParameterization && isSuperParametrization(aClass)) {
        return;
      }
      if (AnnotationUtil.isAnnotated(aClass, ignorableAnnotations, 0)) {
        return;
      }
      if (ignoreThrowables && InheritanceUtil.isInheritor(aClass, "java.lang.Throwable")) {
        return;
      }
      registerClassError(aClass, aClass);
    }

    private boolean hasTypeArguments(PsiReferenceList extendsList) {
      if (extendsList == null) {
        return false;
      }
      final PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
        final PsiReferenceParameterList parameterList = referenceElement.getParameterList();
        if (parameterList == null) {
          continue;
        }
        final PsiType[] typeArguments = parameterList.getTypeArguments();
        if (typeArguments.length != 0) {
          return true;
        }
      }
      return false;
    }

    private boolean isSuperParametrization(PsiClass aClass) {
      if (!(aClass instanceof PsiAnonymousClass)) {
        final PsiReferenceList extendsList = aClass.getExtendsList();
        final PsiReferenceList implementsList = aClass.getImplementsList();
        return hasTypeArguments(extendsList) || hasTypeArguments(implementsList);
      }
      final PsiAnonymousClass anonymousClass = (PsiAnonymousClass)aClass;
      final PsiJavaCodeReferenceElement reference = anonymousClass.getBaseClassReference();
      final PsiReferenceParameterList parameterList = reference.getParameterList();
      if (parameterList == null) {
        return false;
      }
      final PsiTypeElement[] elements = parameterList.getTypeParameterElements();
      for (PsiTypeElement element : elements) {
        if (element != null) {
          return true;
        }
      }
      return false;
    }
  }
}
