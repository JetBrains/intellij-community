/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class EmptyClassInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet ignorableAnnotations = new ExternalizableStringSet();
  @SuppressWarnings({"PublicField", "WeakerAccess"})
  public boolean ignoreClassWithParameterization;
  @SuppressWarnings({"PublicField", "WeakerAccess"})
  public boolean ignoreThrowables = true;
  @SuppressWarnings({"PublicField", "WeakerAccess"})
  public boolean commentsAreContent = true;

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    final JPanel annotationsListControl = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      ignorableAnnotations, InspectionGadgetsBundle.message("ignore.if.annotated.by"));

    panel.add(annotationsListControl, "growx, wrap");
    panel.addCheckbox(InspectionGadgetsBundle.message("empty.class.ignore.parameterization.option"), "ignoreClassWithParameterization");
    panel.addCheckbox(InspectionGadgetsBundle.message("inspection.empty.class.ignore.subclasses.option", CommonClassNames.JAVA_LANG_THROWABLE),
              "ignoreThrowables");
    panel.addCheckbox(InspectionGadgetsBundle.message("comments.as.content.option"), "commentsAreContent");

    return panel;
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    defaultWriteSettings(node, "commentsAreContent");
    writeBooleanOption(node, "commentsAreContent", false);
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

  @Override
  protected InspectionGadgetsFix @NotNull [] buildFixes(Object... infos) {
    final Object info = infos[0];
    if (!(info instanceof PsiModifierListOwner)) {
      return InspectionGadgetsFix.EMPTY_ARRAY;
    }
    List<InspectionGadgetsFix> fixes =
      AddToIgnoreIfAnnotatedByListQuickFix.build((PsiModifierListOwner)info, ignorableAnnotations, new ArrayList<>());
    if (info instanceof PsiAnonymousClass) {
      fixes.add(0, new ConvertEmptyAnonymousToNewFix());
    }
    return fixes.toArray(InspectionGadgetsFix.EMPTY_ARRAY);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EmptyClassVisitor();
  }

  private static class ConvertEmptyAnonymousToNewFix extends InspectionGadgetsFix {
    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element == null) return;
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiAnonymousClass)) return;
      PsiAnonymousClass aClass = (PsiAnonymousClass)parent;
      PsiElement lBrace = aClass.getLBrace();
      PsiElement rBrace = aClass.getRBrace();
      if (lBrace != null && rBrace != null) {
        PsiElement prev = lBrace.getPrevSibling();
        PsiElement start = prev instanceof PsiWhiteSpace ? prev : lBrace;
        Document document = PsiDocumentManager.getInstance(project).getDocument(aClass.getContainingFile());
        if (document == null) return;
        int anonymousStart = start.getTextRange().getStartOffset();
        int rBraceEnd = rBrace.getTextRange().getEndOffset();
        document.deleteString(anonymousStart, rBraceEnd);
      }
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("convert.empty.anonymous.to.new.fix.family.name");
    }
  }

  private class EmptyClassVisitor extends BaseInspectionVisitor {
    @Override
    public void visitFile(@NotNull PsiFile file) {
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
      if (PsiTreeUtil.getChildOfType(aClass, PsiMethod.class) != null ||
          PsiTreeUtil.getChildOfType(aClass, PsiField.class) != null) {
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
      if (ignoreThrowables && InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_THROWABLE)) {
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