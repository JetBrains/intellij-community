// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.inheritance;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInspection.AnnotateMethodFix;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.scopes.ModulesScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.MethodUtils;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class MissingOverrideAnnotationInspection extends BaseInspection implements CleanupLocalInspectionTool{
  @SuppressWarnings("PublicField")
  public boolean ignoreObjectMethods = true;

  @SuppressWarnings("PublicField")
  public boolean ignoreAnonymousClassMethods;

  public boolean warnInSuper = true;

  @Override
  public void writeSettings(@NotNull Element node) {
    defaultWriteSettings(node, "warnInSuper");
    writeBooleanOption(node, "warnInSuper", true);
  }

  @Override
  @NotNull
  public String getID() {
    return "override";
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiMethod method = (PsiMethod)infos[0];
    final boolean annotateMethod = (boolean)infos[1];
    final boolean annotateHierarchy = (boolean)infos[2];
    return new DelegatingFix(createAnnotateFix(method, annotateMethod, annotateHierarchy));
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    final boolean annotateMethod = (boolean)infos[1];
    return InspectionGadgetsBundle.message(annotateMethod
                                           ? "missing.override.annotation.problem.descriptor"
                                           : "missing.override.annotation.in.overriding.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.equals.hashcode.and.tostring"), "ignoreObjectMethods");
    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.methods.in.anonymous.classes"), "ignoreAnonymousClassMethods");
    panel.addCheckbox(InspectionGadgetsBundle.message("missing.override.warn.on.super.option"), "warnInSuper");
    return panel;
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MissingOverrideAnnotationVisitor();
  }

  private class MissingOverrideAnnotationVisitor extends BaseInspectionVisitor {

      @Override
      public void visitMethod(@NotNull PsiMethod method) {
        if (method.getNameIdentifier() == null || method.isConstructor()) {
          return;
        }
        if (method.hasModifierProperty(PsiModifier.PRIVATE) || method.hasModifierProperty(PsiModifier.STATIC)) {
          return;
        }
        final PsiClass methodClass = method.getContainingClass();
        if (methodClass == null) {
          return;
        }
        if (ignoreObjectMethods &&
            (MethodUtils.isHashCode(method) || MethodUtils.isEquals(method) || MethodUtils.isToString(method))) {
          return;
        }

        final boolean annotateMethod = isMissingOverride(method);
        final boolean annotateHierarchy = warnInSuper && isOnTheFly() && isMissingOverrideInOverriders(method);
        if (annotateMethod || annotateHierarchy) {
          registerMethodError(method, method, annotateMethod, annotateHierarchy);
        }
      }

      // we assume:
      // 1) method name is not frequently used
      // 2) most of overridden methods already have @Override annotation
      // 3) only one annotation with short name 'Override' exists: it's 'java.lang.Override'
      private boolean isMissingOverrideInOverriders(@NotNull PsiMethod method) {
        if (!PsiUtil.canBeOverridden(method)) return false;

        Project project = method.getProject();
        final boolean isInterface = Objects.requireNonNull(method.getContainingClass()).isInterface();
        LanguageLevel minimal = isInterface ? LanguageLevel.JDK_1_6 : LanguageLevel.JDK_1_5;

        GlobalSearchScope scope = getLanguageLevelScope(minimal, project);
        if (scope == null) return false;
        int paramCount = method.getParameterList().getParametersCount();
        Predicate<PsiMethod> preFilter = m -> m.getParameterList().getParametersCount() == paramCount &&
                                              !JavaOverridingMethodUtil.containsAnnotationWithName(m, "Override");
        Stream<PsiMethod> overridingMethods = JavaOverridingMethodUtil.getOverridingMethodsIfCheapEnough(method, scope, preFilter);
        return overridingMethods != null && overridingMethods.findAny().isPresent();
      }

      private boolean isMissingOverride(@NotNull PsiMethod method) {
        PsiClass methodClass = method.getContainingClass();
        if (ignoreAnonymousClassMethods && methodClass instanceof PsiAnonymousClass) {
          return false;
        }
        if (hasOverrideAnnotation(method)) {
          return false;
        }
        LanguageLevel level = PsiUtil.getLanguageLevel(method);
        if (JavaPsiRecordUtil.getRecordComponentForAccessor(method) != null) {
          return true;
        }
        final boolean useJdk6Rules = level.isAtLeast(LanguageLevel.JDK_1_6);
        if (useJdk6Rules) {
          if (!isJdk6Override(method, methodClass)) {
            return false;
          }
        }
        else if (!isJdk5Override(method, methodClass)) {
          return false;
        }
        return true;
      }

      private boolean hasOverrideAnnotation(PsiModifierListOwner modifierListOwner) {
        final PsiModifierList modifierList = modifierListOwner.getModifierList();
        if (modifierList != null && modifierList.hasAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE)) {
          return true;
        }
        final ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(modifierListOwner.getProject());
        final List<PsiAnnotation> annotations =
          annotationsManager.findExternalAnnotations(modifierListOwner, CommonClassNames.JAVA_LANG_OVERRIDE);
        return !annotations.isEmpty();
      }

      private boolean isJdk6Override(PsiMethod method, PsiClass methodClass) {
        final PsiMethod[] superMethods = method.findSuperMethods();
        boolean hasSupers = false;
        for (PsiMethod superMethod : superMethods) {
          final PsiClass superClass = superMethod.getContainingClass();
          if (ignoreSuperMethod(method, methodClass, superMethod, superClass)) {
            continue;
          }
          hasSupers = true;
          if (!superMethod.hasModifierProperty(PsiModifier.PROTECTED)) {
            return true;
          }
        }
        // is override except if this is an interface method
        // overriding a protected method in java.lang.Object
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6501053
        return hasSupers && !methodClass.isInterface();
      }

      private boolean isJdk5Override(PsiMethod method, PsiClass methodClass) {
        final PsiMethod[] superMethods = method.findSuperMethods();
        for (PsiMethod superMethod : superMethods) {
          final PsiClass superClass = superMethod.getContainingClass();
          if (ignoreSuperMethod(method, methodClass, superMethod, superClass)) {
            continue;
          }
          if (superClass.isInterface()) {
            continue;
          }
          if (methodClass.isInterface() &&
              superMethod.hasModifierProperty(PsiModifier.PROTECTED)) {
            // only true for J2SE java.lang.Object.clone(), but might
            // be different on other/newer java platforms
            continue;
          }
          return true;
        }
        return false;
      }

      @Contract("_, _, _,null -> true")
      private boolean ignoreSuperMethod(PsiMethod method, PsiClass methodClass, PsiMethod superMethod, PsiClass superClass) {
        return !InheritanceUtil.isInheritorOrSelf(methodClass, superClass, true) ||
               LanguageLevelUtil.getLastIncompatibleLanguageLevel(superMethod, PsiUtil.getLanguageLevel(method)) != null;
      }
  }

  @NotNull
  private static LocalQuickFix createAnnotateFix(@NotNull PsiMethod method, boolean annotateMethod, boolean annotateHierarchy) {
    if (!annotateHierarchy) {
      return new AddAnnotationPsiFix(CommonClassNames.JAVA_LANG_OVERRIDE, method);
    }
    return new AnnotateMethodFix(CommonClassNames.JAVA_LANG_OVERRIDE) {
      @Override
      protected boolean annotateSelf() {
        return annotateMethod;
      }

      @Override
      protected boolean annotateOverriddenMethods() {
        return true;
      }
    };
  }

  @Nullable
  private static GlobalSearchScope getLanguageLevelScope(@NotNull LanguageLevel _minimal, @NotNull Project project) {
    Map<LanguageLevel, GlobalSearchScope> map = CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      Map<LanguageLevel, GlobalSearchScope> result = ConcurrentFactoryMap.createMap(minimal -> {
        Set<Module> modules = StreamEx
          .of(ModuleManager.getInstance(project).getModules())
          .filter(m -> LanguageLevelUtil.getEffectiveLanguageLevel(m).isAtLeast(minimal))
          .toSet();
        return modules == null ? null : new ModulesScope(modules, project);
      });
      return CachedValueProvider.Result.create(result, ProjectRootModificationTracker.getInstance(project));
    });
    return map.get(_minimal);
  }
}
