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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Query;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.AddToIgnoreIfAnnotatedByListQuickFix;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class UtilityClassWithoutPrivateConstructorInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet ignorableAnnotations = new ExternalizableStringSet();
  @SuppressWarnings("PublicField")
  public boolean ignoreClassesWithOnlyMain = false;

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    final JPanel annotationsPanel = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      ignorableAnnotations, InspectionGadgetsBundle.message("ignore.if.annotated.by"));
    panel.add(annotationsPanel, "growx, wrap");
    panel.addCheckbox(InspectionGadgetsBundle.message("utility.class.without.private.constructor.option"), "ignoreClassesWithOnlyMain");
    return panel;
  }

  @Override
  protected InspectionGadgetsFix @NotNull [] buildFixes(Object... infos) {
    final List<InspectionGadgetsFix> fixes = new ArrayList<>();
    final PsiClass aClass = (PsiClass)infos[0];
    final PsiMethod constructor = getNullArgConstructor(aClass);
    final boolean isOnTheFly = (boolean)infos[1];
    if (constructor == null) {
      if (isOnTheFly || !hasImplicitConstructorUsage(aClass)) {
        fixes.add(new CreateEmptyPrivateConstructor());
      }
    }
    else {
      final Query<PsiReference> query = ReferencesSearch.search(constructor, constructor.getUseScope());
      final PsiReference reference = query.findFirst();
      if (reference == null) {
        fixes.add(new MakeConstructorPrivateFix());
      }
    }
    AddToIgnoreIfAnnotatedByListQuickFix.build(aClass, ignorableAnnotations, fixes);
    return fixes.toArray(InspectionGadgetsFix.EMPTY_ARRAY);
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("utility.class.without.private.constructor.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UtilityClassWithoutPrivateConstructorVisitor();
  }

  private static boolean hasImplicitConstructorUsage(PsiClass aClass) {
    final Query<PsiReference> query = ReferencesSearch.search(aClass, aClass.getUseScope());
    for (PsiReference reference : query) {
      if (reference == null) continue;
      final PsiElement element = reference.getElement();
      final PsiElement context = element.getParent();
      if (context instanceof PsiNewExpression) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  static PsiMethod getNullArgConstructor(PsiClass aClass) {
    final PsiMethod[] constructors = aClass.getConstructors();
    for (final PsiMethod constructor : constructors) {
      final PsiParameterList params = constructor.getParameterList();
      if (params.isEmpty()) {
        return constructor;
      }
    }
    return null;
  }

  protected static class CreateEmptyPrivateConstructor extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("utility.class.without.private.constructor.create.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement classNameIdentifier = descriptor.getPsiElement();
      final PsiElement parent = classNameIdentifier.getParent();
      if (!(parent instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)parent;
      if (isOnTheFly() && hasImplicitConstructorUsage(aClass)) {
        SwingUtilities.invokeLater(() -> Messages.showInfoMessage(
          aClass.getProject(),
          InspectionGadgetsBundle.message("utility.class.without.private.constructor.cant.generate.constructor.message"),
          InspectionGadgetsBundle.message("utility.class.without.private.constructor.cant.generate.constructor.title")));
      }
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = psiFacade.getElementFactory();
      final PsiMethod constructor = factory.createConstructor();
      final PsiModifierList modifierList = constructor.getModifierList();
      modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
      aClass.add(constructor);
      final CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
      styleManager.reformat(constructor);
    }
  }

  private static class MakeConstructorPrivateFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("utility.class.without.private.constructor.make.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement classNameIdentifier = descriptor.getPsiElement();
      final PsiElement parent = classNameIdentifier.getParent();
      if (!(parent instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)parent;
      final PsiMethod[] constructors = aClass.getConstructors();
      for (final PsiMethod constructor : constructors) {
        final PsiParameterList parameterList = constructor.getParameterList();
        if (parameterList.isEmpty()) {
          final PsiModifierList modifiers = constructor.getModifierList();
          modifiers.setModifierProperty(PsiModifier.PUBLIC, false);
          modifiers.setModifierProperty(PsiModifier.PROTECTED, false);
          modifiers.setModifierProperty(PsiModifier.PRIVATE, true);
        }
      }
    }
  }

  private class UtilityClassWithoutPrivateConstructorVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so that it doesn't drill down to inner classes
      if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      if (!UtilityClassUtil.isUtilityClass(aClass)) {
        return;
      }
      if (ignoreClassesWithOnlyMain && hasOnlyMain(aClass)) {
        return;
      }
      if (hasPrivateConstructor(aClass)) {
        return;
      }
      if (AnnotationUtil.isAnnotated(aClass, ignorableAnnotations, 0)) {
        return;
      }
      if (aClass.hasModifierProperty(PsiModifier.PRIVATE) && aClass.getConstructors().length == 0) {
        return;
      }
      final SearchScope scope = GlobalSearchScope.projectScope(aClass.getProject());
      final Query<PsiClass> query = ClassInheritorsSearch.search(aClass, scope, true);
      final PsiClass subclass = query.findFirst();
      if (subclass != null) {
        return;
      }
      registerClassError(aClass, aClass, isOnTheFly());
    }

    private boolean hasOnlyMain(PsiClass aClass) {
      final PsiMethod[] methods = aClass.getMethods();
      if (methods.length == 0) {
        return false;
      }
      for (PsiMethod method : methods) {
        if (method.isConstructor()) {
          continue;
        }
        if (!method.hasModifierProperty(PsiModifier.STATIC)) {
          return false;
        }
        if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
          continue;
        }
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
          return false;
        }
        final String name = method.getName();
        if (!name.equals(HardcodedMethodConstants.MAIN)) {
          return false;
        }
        final PsiType returnType = method.getReturnType();
        if (!PsiType.VOID.equals(returnType)) {
          return false;
        }
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 1) {
          return false;
        }
        final PsiParameter[] parameters = parameterList.getParameters();
        final PsiParameter parameter = parameters[0];
        final PsiType type = parameter.getType();
        @NonNls final String stringArray = "java.lang.String[]";
        if (!type.equalsToText(stringArray)) {
          return false;
        }
      }
      return true;
    }

    boolean hasPrivateConstructor(PsiClass aClass) {
      final PsiMethod[] constructors = aClass.getConstructors();
      for (final PsiMethod constructor : constructors) {
        if (constructor.hasModifierProperty(PsiModifier.PRIVATE)) {
          return true;
        }
      }
      return false;
    }
  }
}
