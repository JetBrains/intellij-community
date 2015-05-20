/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AbstractMethodOverridesAbstractMethodInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreJavaDoc = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreAnnotations = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("abstract.method.overrides.abstract.method.display.name");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new AbstractMethodOverridesAbstractMethodFix();
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("abstract.method.overrides.abstract.method.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message(
      "abstract.method.overrides.abstract.method.ignore.different.javadoc.option"), "ignoreJavaDoc");
    panel.addCheckbox(InspectionGadgetsBundle.message(
      "abstract.method.overrides.abstract.method.ignore.different.annotations.option"), "ignoreAnnotations");
    return panel;
  }

  private static class AbstractMethodOverridesAbstractMethodFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("abstract.method.overrides.abstract.method.remove.quickfix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement methodNameIdentifier = descriptor.getPsiElement();
      final PsiElement method = methodNameIdentifier.getParent();
      assert method != null;
      deleteElement(method);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AbstractMethodOverridesAbstractMethodVisitor();
  }

  private class AbstractMethodOverridesAbstractMethodVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      //no call to super, so we don't drill into anonymous classes
      if (method.isConstructor()) {
        return;
      }
      if (!isAbstract(method)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (!method.hasModifierProperty(PsiModifier.ABSTRACT) && !containingClass.isInterface()) {
        return;
      }
      boolean overrideDefault = false;
      boolean accept = false;
      final PsiMethod[] superMethods = method.findSuperMethods();
      for (final PsiMethod superMethod : superMethods) {
        overrideDefault |= superMethod.hasModifierProperty(PsiModifier.DEFAULT);
        if (!isAbstract(superMethod)) {
          continue;
        }
        if (overrideDefault) {
          return;
        }
        accept |= methodsHaveSameReturnTypes(method, superMethod) && haveSameExceptionSignatures(method, superMethod);

        if (ignoreJavaDoc && !haveSameJavaDoc(method, superMethod)) {
          return;
        }
        if (ignoreAnnotations && !methodsHaveSameAnnotations(method, superMethod)) {
          return;
        }
      }
      if (accept && !overrideDefault) {
        registerMethodError(method);
      }
    }

    private boolean methodsHaveSameAnnotations(PsiMethod method, PsiMethod superMethod) {
      if (!haveSameAnnotations(method, superMethod)) {
        return false;
      }
      final PsiParameterList superParameterList = superMethod.getParameterList();
      final PsiParameter[] superParameters = superParameterList.getParameters();
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      for (int i = 0, length = superParameters.length; i < length; i++) {
        final PsiParameter superParameter = superParameters[i];
        final PsiParameter parameter = parameters[i];
        if (!haveSameAnnotations(parameter, superParameter)) {
          return false;
        }
      }
      return true;
    }

    private boolean haveSameAnnotations(PsiModifierListOwner owner1, PsiModifierListOwner owner2) {
      final PsiModifierList modifierList = owner1.getModifierList();
      final PsiModifierList superModifierList = owner2.getModifierList();
      if (superModifierList == null) {
        return modifierList == null;
      } else if (modifierList == null) {
        return false;
      }
      final PsiAnnotation[] superAnnotations = superModifierList.getAnnotations();
      final PsiAnnotation[] annotations = modifierList.getAnnotations();
      final Set<PsiAnnotation> annotationsSet = new HashSet<PsiAnnotation>(Arrays.asList(superAnnotations));
      for (PsiAnnotation annotation : annotations) {
        final String qualifiedName = annotation.getQualifiedName();
        if ("java.lang.Override".equals(qualifiedName)) {
          continue;
        }
        if (!annotationsSet.contains(annotation)) {
          return false;
        }
      }
      return true;
    }

    private boolean haveSameJavaDoc(PsiMethod method, PsiMethod superMethod) {
      final PsiDocComment superDocComment = superMethod.getDocComment();
      final PsiDocComment docComment = method.getDocComment();
      if (superDocComment == null) {
        if (docComment != null) {
          return false;
        }
      } else if (docComment != null) {
        if (!superDocComment.getText().equals(docComment.getText())) {
          return false;
        }
      }
      return true;
    }

    private boolean haveSameExceptionSignatures(PsiMethod method1, PsiMethod method2) {
      final PsiReferenceList list1 = method1.getThrowsList();
      final PsiClassType[] exceptions1 = list1.getReferencedTypes();
      final PsiReferenceList list2 = method2.getThrowsList();
      final PsiClassType[] exceptions2 = list2.getReferencedTypes();
      if (exceptions1.length != exceptions2.length) {
        return false;
      }
      final Set<PsiClassType> set1 = new HashSet<PsiClassType>(Arrays.asList(exceptions1));
      for (PsiClassType anException : exceptions2) {
        if (!set1.contains(anException)) {
          return false;
        }
      }
      return true;
    }

    private boolean methodsHaveSameReturnTypes(PsiMethod method1, PsiMethod method2) {
      final PsiType type1 = method1.getReturnType();
      if (type1 == null) {
        return false;
      }
      final PsiClass superClass = method2.getContainingClass();
      final PsiClass aClass = method1.getContainingClass();
      if (aClass == null || superClass == null) return false;
      final PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
      final PsiType type2 = method2.getReturnType();
      return Comparing.equal(type1, substitutor.substitute(type2));
    }

    private boolean isAbstract(PsiMethod method) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return true;
      }
      final PsiClass containingClass = method.getContainingClass();
      return containingClass != null && containingClass.isInterface() && !method.hasModifierProperty(PsiModifier.DEFAULT);
    }
  }
}