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
import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
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
import com.siyeh.ig.fixes.AddToIgnoreIfAnnotatedByListQuickFix;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.*;

public class UtilityClassWithoutPrivateConstructorInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet ignorableAnnotations = new ExternalizableStringSet();
  @SuppressWarnings("PublicField")
  public boolean ignoreClassesWithOnlyMain = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      stringList("ignorableAnnotations", InspectionGadgetsBundle.message("ignore.if.annotated.by"),
                 new JavaClassValidator().annotationsOnly()),
      checkbox("ignoreClassesWithOnlyMain", InspectionGadgetsBundle.message("utility.class.without.private.constructor.option"))
    );
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final List<LocalQuickFix> fixes = new ArrayList<>();
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
    return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
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
    return query.anyMatch(ref -> ref != null && ref.getElement().getParent() instanceof PsiNewExpression);
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

  protected static class CreateEmptyPrivateConstructor extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("utility.class.without.private.constructor.create.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement classNameIdentifier, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = classNameIdentifier.getParent();
      if (!(parent instanceof PsiClass aClass)) {
        return;
      }
      if (hasImplicitConstructorUsage(aClass)) {
        updater.cancel(InspectionGadgetsBundle.message("utility.class.without.private.constructor.cant.generate.constructor.message"));
        return;
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

  private static class MakeConstructorPrivateFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("utility.class.without.private.constructor.make.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement classNameIdentifier, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = classNameIdentifier.getParent();
      if (!(parent instanceof PsiClass aClass)) {
        return;
      }
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

    private static boolean hasOnlyMain(PsiClass aClass) {
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
        if (!PsiTypes.voidType().equals(returnType)) {
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
