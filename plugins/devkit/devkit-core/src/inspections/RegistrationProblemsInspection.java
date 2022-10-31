// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.daemon.impl.quickfix.ImplementOrExtendFix;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.quickfix.CreateConstructorFix;
import org.jetbrains.idea.devkit.util.ActionType;

import java.util.List;
import java.util.Set;

public class RegistrationProblemsInspection extends DevKitInspectionBase {

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "ComponentRegistrationProblems";
  }

  @Override
  public ProblemDescriptor @Nullable [] checkClass(@NotNull PsiClass checkedClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final PsiIdentifier nameIdentifier = checkedClass.getNameIdentifier();
    if (nameIdentifier != null &&
        checkedClass.getQualifiedName() != null &&
        checkedClass.getContainingFile().getVirtualFile() != null &&
        !checkedClass.isInterface() &&
        !checkedClass.isEnum() &&
        !checkedClass.hasModifierProperty(PsiModifier.PRIVATE) &&
        !checkedClass.hasModifierProperty(PsiModifier.PROTECTED) &&
        !PsiUtil.isInnerClass(checkedClass)) {
      final RegistrationCheckerUtil.RegistrationType registrationType = RegistrationCheckerUtil.RegistrationType.ALL;
      final Set<PsiClass> componentClasses = RegistrationCheckerUtil.getRegistrationTypes(checkedClass, registrationType);
      if (componentClasses != null && !componentClasses.isEmpty()) {
        List<ProblemDescriptor> problems = new SmartList<>();

        for (PsiClass componentClass : componentClasses) {
          if (ActionType.ACTION.myClassName.equals(componentClass.getQualifiedName()) &&
              !checkedClass.isInheritor(componentClass, true)) {
            problems.add(manager.createProblemDescriptor(nameIdentifier,
                                                         DevKitBundle.message("inspections.registration.problems.incompatible.message",
                                                                              componentClass.getQualifiedName()), isOnTheFly,
                                                         ImplementOrExtendFix.createFixes(nameIdentifier, componentClass, checkedClass,
                                                                                          isOnTheFly),
                                                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }
        }
        if (ActionType.ACTION.isOfType(checkedClass)) {
          if (ConstructorType.getNoArgCtor(checkedClass) == null) {
            problems.add(manager.createProblemDescriptor(nameIdentifier,
                                                         DevKitBundle.message("inspections.registration.problems.missing.noarg.ctor"),
                                                         new CreateConstructorFix(checkedClass, isOnTheFly),
                                                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly));
          }
        }
        if (checkedClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
          problems.add(manager.createProblemDescriptor(nameIdentifier,
                                                       DevKitBundle.message("inspections.registration.problems.abstract"), isOnTheFly,
                                                       LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
        return problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
      }
    }
    return null;
  }

  static class ConstructorType {
    static final ConstructorType DEFAULT = new ConstructorType();
    final PsiMethod myCtor;

    private ConstructorType() {
      myCtor = null;
    }

    protected ConstructorType(PsiMethod ctor) {
      assert ctor != null;
      myCtor = ctor;
    }

    public static ConstructorType getNoArgCtor(PsiClass checkedClass) {
      final PsiMethod[] constructors = checkedClass.getConstructors();
      if (constructors.length > 0) {
        for (PsiMethod ctor : constructors) {
          if (ctor.getParameterList().isEmpty()) {
            return new ConstructorType(ctor);
          }
        }
        return null;
      }
      return DEFAULT;
    }
  }
}
