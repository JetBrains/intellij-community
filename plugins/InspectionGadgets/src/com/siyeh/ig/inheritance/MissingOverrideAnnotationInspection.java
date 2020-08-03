// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.java15api.Java15APIUsageInspection;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.module.EffectiveLanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.scopes.ModulesScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.JavaOverridingMethodUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class MissingOverrideAnnotationInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool{
  private static final String OVERRIDE_SHORT_NAME = StringUtil.getShortName(CommonClassNames.JAVA_LANG_OVERRIDE);

  @SuppressWarnings("PublicField")
  public boolean ignoreObjectMethods = true;

  @SuppressWarnings("PublicField")
  public boolean ignoreAnonymousClassMethods;

  @Override
  @NotNull
  public String getID() {
    return "override";
  }

  /**
   * @deprecated
   * Use {@link AnnotateMethodFix}. To be removed in 2021.1.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  @SuppressWarnings("unused")
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new InspectionGadgetsFix() {
      private final AnnotateMethodFix myFix = new AnnotateMethodFix(CommonClassNames.JAVA_LANG_OVERRIDE);

      @Override
      protected void doFix(Project project, ProblemDescriptor descriptor) {
        myFix.applyFix(project, descriptor);
      }

      @Nls
      @NotNull
      @Override
      public String getFamilyName() {
        return myFix.getFamilyName();
      }
    };
  }

  /**
   * @deprecated To be removed in 2021.1.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  @SuppressWarnings("unused")
  protected String buildErrorString(Object... infos) {
    throw new UnsupportedOperationException();
  }

  /**
   * @deprecated  To be removed in 2021.1.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  protected BaseInspectionVisitor buildVisitor() {
    throw new UnsupportedOperationException();
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.equals.hashcode.and.tostring"), "ignoreObjectMethods");
    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.methods.in.anonymous.classes"), "ignoreAnonymousClassMethods");
    return panel;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel5OrHigher(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitMethod(@NotNull PsiMethod method) {
        if (method.getNameIdentifier() == null) {
          return;
        }
        if (method.isConstructor()) {
          return;
        }
        if (method.hasModifierProperty(PsiModifier.PRIVATE) ||
            method.hasModifierProperty(PsiModifier.STATIC)) {
          return;
        }
        final PsiClass methodClass = method.getContainingClass();
        if (methodClass == null) {
          return;
        }
        if (ignoreObjectMethods && (MethodUtils.isHashCode(method) ||
                                    MethodUtils.isEquals(method) ||
                                    MethodUtils.isToString(method))) {
          return;
        }

        InspectionResult result = new InspectionResult();
        checkMissingOverride(method, result);
        if (isOnTheFly) {
          checkMissingOverrideInOverriders(method, result);
        }

        if (result.requireAnnotation || result.hierarchyAnnotated == ThreeState.NO) {
          holder.registerProblem(method.getNameIdentifier(),
                                 InspectionGadgetsBundle.message(result.requireAnnotation
                                                                 ? "missing.override.annotation.problem.descriptor"
                                                                 : "missing.override.annotation.in.overriding.problem.descriptor"),
                                 createAnnotateFix(result.requireAnnotation, result.hierarchyAnnotated));
        }
      }

      // we assume:
      // 1) method name is not frequently used
      // 2) most of overridden methods already have @Override annotation
      // 3) only one annotation with short name 'Override' exists: it's 'java.lang.Override'
      private void checkMissingOverrideInOverriders(@NotNull PsiMethod method,
                                                    @NotNull InspectionResult result) {
        if (!PsiUtil.canBeOverridden(method)) return;

        Project project = method.getProject();
        LanguageLevel minimal = Objects.requireNonNull(method.getContainingClass()).isInterface() ? LanguageLevel.JDK_1_6 : LanguageLevel.JDK_1_5;

        GlobalSearchScope scope = getLanguageLevelScope(minimal, project);
        if (scope == null) return;
        int paramCount = method.getParameterList().getParametersCount();
        Predicate<PsiMethod> preFilter = m -> m.getParameterList().getParametersCount() == paramCount &&
                                              !JavaOverridingMethodUtil.containsAnnotationWithName(m, OVERRIDE_SHORT_NAME);
        Stream<PsiMethod> overridingMethods = JavaOverridingMethodUtil.getOverridingMethodsIfCheapEnough(method, scope, preFilter);
        if (overridingMethods == null) return;
        result.hierarchyAnnotated = ThreeState.fromBoolean(!overridingMethods.findAny().isPresent());
      }

      private void checkMissingOverride(@NotNull PsiMethod method,
                                          @NotNull InspectionResult result) {
        PsiClass methodClass = method.getContainingClass();
        if (ignoreAnonymousClassMethods && methodClass instanceof PsiAnonymousClass) {
          return;
        }
        if (hasOverrideAnnotation(method)) {
          return;
        }
        LanguageLevel level = PsiUtil.getLanguageLevel(method);
        if (level != LanguageLevel.JDK_14_PREVIEW && JavaPsiRecordUtil.getRecordComponentForAccessor(method) != null) {
          result.requireAnnotation = true;
          return;
        }
        final boolean useJdk6Rules = level.isAtLeast(LanguageLevel.JDK_1_6);
        if (useJdk6Rules) {
          if (!isJdk6Override(method, methodClass)) {
            return;
          }
        }
        else if (!isJdk5Override(method, methodClass)) {
          return;
        }
        result.requireAnnotation = true;
      }

      private boolean hasOverrideAnnotation(PsiModifierListOwner element) {
        final PsiModifierList modifierList = element.getModifierList();
        return modifierList != null && modifierList.hasAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE);
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
               Java15APIUsageInspection.getLastIncompatibleLanguageLevel(superMethod, PsiUtil.getLanguageLevel(method)) != null;
      }
    };
  }

  @NotNull
  private static AnnotateMethodFix createAnnotateFix(final boolean requireAnnotation, final ThreeState hierarchyAnnotated) {
    return new AnnotateMethodFix(CommonClassNames.JAVA_LANG_OVERRIDE) {
      @Override
      protected boolean annotateSelf() {
        return requireAnnotation;
      }

      @Override
      protected boolean annotateOverriddenMethods() {
        return hierarchyAnnotated == ThreeState.NO;
      }
    };
  }

  private static class InspectionResult {
    private boolean requireAnnotation;
    private ThreeState hierarchyAnnotated = ThreeState.UNSURE;
  }

  @Nullable
  private static GlobalSearchScope getLanguageLevelScope(@NotNull LanguageLevel _minimal, @NotNull Project project) {
    Map<LanguageLevel, GlobalSearchScope> map = CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      Map<LanguageLevel, GlobalSearchScope> result = ConcurrentFactoryMap.createMap(minimal -> {
        Set<Module> modules = StreamEx
          .of(ModuleManager.getInstance(project).getModules())
          .filter(m -> EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(m).isAtLeast(minimal))
          .toSet();
        return modules == null ? null : new ModulesScope(modules, project);
      });
      return CachedValueProvider.Result.create(result, ProjectRootModificationTracker.getInstance(project));
    });
    return map.get(_minimal);
  }
}
