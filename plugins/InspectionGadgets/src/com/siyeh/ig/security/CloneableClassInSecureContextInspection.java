/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.security;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CloneUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class CloneableClassInSecureContextInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("cloneable.class.in.secure.context.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("cloneable.class.in.secure.context.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    // the quickfixes below probably require some thought and shouldn't be applied blindly on many classes at once
    return true;
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    if (CloneUtils.isDirectlyCloneable(aClass)) {
      return new RemoveCloneableFix();
    }
    final boolean hasCloneMethod = Arrays.stream(aClass.findMethodsByName("clone", false)).anyMatch(CloneUtils::isClone);
    if (hasCloneMethod) {
      return null;
    }
    return new CreateExceptionCloneMethodFix();
  }

  private static class RemoveCloneableFix extends InspectionGadgetsFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("remove.cloneable.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement().getParent();
      if (!(element instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)element;
      final PsiReferenceList implementsList = aClass.getImplementsList();
      if (implementsList == null) {
        return;
      }
      final PsiClass cloneableClass = ClassUtils.findClass(CommonClassNames.JAVA_LANG_CLONEABLE, element);
      if (cloneableClass == null) {
        return;
      }
      final PsiJavaCodeReferenceElement[] referenceElements = implementsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
        final PsiElement target = referenceElement.resolve();
        if (cloneableClass.equals(target)) {
          referenceElement.delete();
          return;
        }
      }
    }
  }

  private static class CreateExceptionCloneMethodFix extends InspectionGadgetsFix {

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("cloneable.class.in.secure.context.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement().getParent();
      if (!(element instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)element;
      final StringBuilder methodText = new StringBuilder();
      if (PsiUtil.isLanguageLevel5OrHigher(aClass) && CodeStyleSettingsManager.getSettings(aClass.getProject())
        .getCustomSettings(JavaCodeStyleSettings.class).INSERT_OVERRIDE_ANNOTATION) {
        methodText.append("@java.lang.Override ");
      }
      methodText.append("protected ").append(aClass.getName());
      final PsiTypeParameterList typeParameterList = aClass.getTypeParameterList();
      if (typeParameterList != null) {
        methodText.append(typeParameterList.getText());
      }
      methodText.append(" clone() {}");
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiMethod method = (PsiMethod)aClass.add(factory.createMethodFromText(methodText.toString(), aClass));
      final PsiClassType exceptionType = factory.createTypeByFQClassName("java.lang.CloneNotSupportedException", element.getResolveScope());
      final PsiMethod superMethod = MethodUtils.getSuper(method);
      boolean throwException = false;
      if (superMethod != null) {
        if (superMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
          method.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
        }
        for (PsiClassType thrownType : superMethod.getThrowsList().getReferencedTypes()) {
          if (thrownType.equals(exceptionType)) {
            throwException = true;
            break;
          }
        }
        if (throwException) {
          final PsiJavaCodeReferenceElement exceptionReference = factory.createReferenceElementByType(exceptionType);
          method.getThrowsList().add(exceptionReference);
        }
        else {
          final PsiJavaCodeReferenceElement errorReference =
            factory.createFQClassNameReferenceElement("java.lang.AssertionError", element.getResolveScope());
          method.getThrowsList().add(errorReference);
        }
      }
      final String throwableName = throwException ? "java.lang.CloneNotSupportedException" : "java.lang.AssertionError";
      final PsiStatement statement = factory.createStatementFromText("throw new " + throwableName + "();", element);
      final PsiCodeBlock body = method.getBody();
      assert body != null;
      body.add(statement);
      if (isOnTheFly()) {
        final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor != null) {
          GenerateMembersUtil.positionCaret(editor, method, true);
        }
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CloneableClassInSecureContextVisitor();
  }

  private static class CloneableClassInSecureContextVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.isInterface() || aClass.isAnnotationType() || aClass instanceof PsiTypeParameter) {
        return;
      }
      if (!CloneUtils.isCloneable(aClass)) {
        return;
      }
      for (final PsiMethod method : aClass.findMethodsByName("clone", true)) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
          // optimization
          break;
        }
        if (CloneUtils.isClone(method) && ControlFlowUtils.methodAlwaysThrowsException((PsiMethod)method.getNavigationElement())) {
          return;
        }
      }
      registerClassError(aClass, aClass);
    }
  }
}