/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInspection.AnnotateMethodFix;
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ThreeState;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class MissingOverrideAnnotationInspection extends BaseJavaBatchLocalInspectionTool implements CleanupLocalInspectionTool{
  private static final int MAX_OVERRIDDEN_METHOD_SEARCH = 20;
  private static final String OVERRIDE_SHORT_NAME = StringUtil.getShortName(CommonClassNames.JAVA_LANG_OVERRIDE);

  @SuppressWarnings({"PublicField"})
  public boolean ignoreObjectMethods = true;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreAnonymousClassMethods = false;

  @Override
  @NotNull
  public String getID() {
    return "override";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("missing.override.annotation.display.name");
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
                                 new AnnotateMethodFix(CommonClassNames.JAVA_LANG_OVERRIDE) {
                                   @Override
                                   protected boolean annotateSelf() {
                                     return result.requireAnnotation;
                                   }

                                   @Override
                                   protected boolean annotateOverriddenMethods() {
                                     return result.hierarchyAnnotated == ThreeState.NO;
                                   }
                                 });
        }
      }

      // we assume:
      // 1) method name is not frequently used
      // 2) most of overridden methods already have @Override annotation
      // 3) only one annotation with short name 'Override' exists: it's 'java.lang.Override'
      private void checkMissingOverrideInOverriders(@NotNull PsiMethod method,
                                                    @NotNull InspectionResult result) {
        if (method.hasModifierProperty(PsiModifier.FINAL)) {
          return;
        }

        Project project = method.getProject();
        String name = method.getName();
        SearchScope useScope = method.getUseScope();
        GlobalSearchScope searchScope = GlobalSearchScopeUtil.toGlobalSearchScope(useScope, project);
        PsiMethod[] methods =
          StubIndex.getElements(JavaStubIndexKeys.METHODS, name, project, searchScope, PsiMethod.class)
          .stream()
          .filter(m -> {
            if (m == method) return false;
            for (PsiAnnotation annotation : m.getModifierList().getAnnotations()) {
              PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
              if (ref != null && OVERRIDE_SHORT_NAME.equals(ref.getReferenceName())) {
                return false;
              }
            }
            return true;
          })
          .limit(MAX_OVERRIDDEN_METHOD_SEARCH + 1)
          .toArray(PsiMethod[]::new);

        // search should be deterministic
        if (methods.length > MAX_OVERRIDDEN_METHOD_SEARCH) {
          return;
        }

        boolean isOverrideApplicableToJava5Methods = !Objects.requireNonNull(method.getContainingClass()).isInterface();

        for (PsiMethod candidate : methods) {
          if ((isOverrideApplicableToJava5Methods || PsiUtil.isLanguageLevel6OrHigher(candidate)) && PsiSuperMethodUtil.isSuperMethod(candidate, method)) {
            result.hierarchyAnnotated = ThreeState.NO;
            return;
          }
        }

        Predicate<PsiMethod> isOverrider = candidate -> (isOverrideApplicableToJava5Methods || PsiUtil.isLanguageLevel6OrHigher(candidate))
                                                        && PsiSuperMethodUtil.isSuperMethod(candidate, method);
        result.hierarchyAnnotated = ThreeState.fromBoolean(Stream.of(methods).noneMatch(isOverrider));
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
        final boolean useJdk6Rules = PsiUtil.isLanguageLevel6OrHigher(method);
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
        return modifierList != null && modifierList.findAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE) != null;
      }

      private boolean isJdk6Override(PsiMethod method, PsiClass methodClass) {
        final PsiMethod[] superMethods = method.findSuperMethods();
        boolean hasSupers = false;
        for (PsiMethod superMethod : superMethods) {
          final PsiClass superClass = superMethod.getContainingClass();
          if (!InheritanceUtil.isInheritorOrSelf(methodClass, superClass, true)) {
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
          if (superClass == null || !InheritanceUtil.isInheritorOrSelf(methodClass, superClass, true)) {
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
    };
  }

  private static class InspectionResult {
    private boolean requireAnnotation = false;
    private ThreeState hierarchyAnnotated = ThreeState.UNSURE;
  }
}
