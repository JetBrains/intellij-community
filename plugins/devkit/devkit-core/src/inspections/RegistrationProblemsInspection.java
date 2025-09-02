// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.ImplementOrExtendFix;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.*;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.*;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.util.ActionType;
import org.jetbrains.uast.UClass;

import java.util.Set;

@ApiStatus.Internal
public final class RegistrationProblemsInspection extends DevKitUastInspectionBase {
  @VisibleForTesting
  public RegistrationProblemsInspection() {
    super(UClass.class);
  }

  @Override
  public ProblemDescriptor @Nullable [] checkClass(@NotNull UClass uClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    PsiClass checkedClass = uClass.getJavaPsi();
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
        PsiElement sourcePsi = uClass.getSourcePsi();
        if (sourcePsi == null) return null;

        ProblemsHolder holder = createProblemsHolder(uClass, manager, isOnTheFly);

        for (PsiClass componentClass : componentClasses) {
          if (ActionType.ACTION.myClassName.equals(componentClass.getQualifiedName()) &&
              !checkedClass.isInheritor(componentClass, true)) {
            ModCommandAction fix = sourcePsi.getLanguage().is(JavaLanguage.INSTANCE) ?
                                   ImplementOrExtendFix.createFix(checkedClass, componentClass) :
                                   null;
            ProblemHolderUtilKt.registerUProblem(holder, uClass,
                                                 DevKitBundle.message("inspections.registration.problems.incompatible.message",
                                                                      componentClass.getQualifiedName()),
                                                 LocalQuickFix.notNullElements(LocalQuickFix.from(fix)));
          }
        }
        if (ActionType.ACTION.isOfType(checkedClass) && !hasNoArgConstructor(checkedClass)) {
          // creating a constructor with UAST is not possible until IDEA-303510 is solved
          LocalQuickFix[] fixes = sourcePsi.getLanguage().is(JavaLanguage.INSTANCE) ?
                                  new LocalQuickFix[]{new CreateConstructorFix(checkedClass, isOnTheFly)} :
                                  LocalQuickFix.EMPTY_ARRAY;
          ProblemHolderUtilKt.registerUProblem(holder, uClass, DevKitBundle.message("inspections.registration.problems.missing.noarg.ctor"),
                                               fixes);
        }
        if (checkedClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
          ProblemHolderUtilKt.registerUProblem(holder, uClass, DevKitBundle.message("inspections.registration.problems.abstract"));
        }
        return holder.getResultsArray();
      }
    }
    return null;
  }

  private static boolean hasNoArgConstructor(PsiClass checkedClass) {
    final PsiMethod[] constructors = checkedClass.getConstructors();
    if (constructors.length == 0) return true; // default constructor
    for (PsiMethod constructor : constructors) {
      if (constructor.getParameterList().isEmpty()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public @NotNull @NonNls String getShortName() {
    return "ComponentRegistrationProblems";
  }

  private static class CreateConstructorFix implements LocalQuickFix {
    protected final SmartPsiElementPointer<? extends PsiElement> myPointer;
    protected final boolean myOnTheFly;

    CreateConstructorFix(@NotNull PsiClass aClass, boolean isOnTheFly) {
      myPointer = SmartPointerManager.createPointer((PsiElement)aClass);
      myOnTheFly = isOnTheFly;
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      return IntentionPreviewInfo.EMPTY;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      // can happen during batch-inspection if resolution has already been applied to plugin.xml or java class
      PsiElement element = myPointer.getElement();
      if (element == null || !element.isValid()) return;

      boolean external = descriptor.getPsiElement().getContainingFile() != element.getContainingFile();
      if (external) {
        PsiClass clazz = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
        if (!ReadonlyStatusHandler.ensureFilesWritable(project, element.getContainingFile().getVirtualFile())) {
          String className = clazz != null ? clazz.getQualifiedName() : element.getContainingFile().getName();
          Messages.showErrorDialog(project,
                                   DevKitBundle.message("inspections.registration.problems.quickfix.read-only", className),
                                   CommonBundle.getErrorTitle());
          return;
        }
      }

      try {
        if (!(element instanceof PsiClass clazz)) return;

        PsiMethod ctor = JavaPsiFacade.getInstance(clazz.getProject()).getElementFactory().createConstructor();
        PsiUtil.setModifierProperty(ctor, PsiModifier.PUBLIC, true);

        PsiMethod[] constructors = clazz.getConstructors();
        if (constructors.length > 0) {
          ctor = (PsiMethod)clazz.addBefore(ctor, constructors[0]);
        }
        else {
          // shouldn't get here - it's legal if there's no ctor present at all
          ctor = (PsiMethod)clazz.add(ctor);
        }

        if (myOnTheFly) {
          ctor.navigate(true);
        }
      }
      catch (IncorrectOperationException e) {
        Logger.getInstance(getClass()).error(e);
      }
    }

    @Override
    public @NotNull String getFamilyName() {
      return DevKitBundle.message("inspections.registration.problems.quickfix.create.constructor");
    }
  }
}
