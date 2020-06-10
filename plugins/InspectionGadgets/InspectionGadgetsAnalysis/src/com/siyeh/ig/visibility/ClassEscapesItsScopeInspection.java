/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.visibility;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import gnu.trove.THashSet;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ClassEscapesItsScopeInspection extends AbstractBaseJavaLocalInspectionTool {

  @SuppressWarnings("PublicField") public boolean checkModuleApi = true; // public & protected fields & methods within exported packages
  @SuppressWarnings("PublicField") public boolean checkPublicApi; // All public & protected fields & methods
  @SuppressWarnings("PublicField") public boolean checkPackageLocal;

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "ClassEscapesDefinedScope";
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("class.escapes.defined.scope.display.module.option"), "checkModuleApi");
    panel.addCheckbox(InspectionGadgetsBundle.message("class.escapes.defined.scope.display.public.option"), "checkPublicApi");
    panel.addCheckbox(InspectionGadgetsBundle.message("class.escapes.defined.scope.display.package.option"), "checkPackageLocal");
    return panel;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    List<VisibilityChecker> checkers = new ArrayList<>(2);
    if (checkModuleApi) {
      PsiFile file = holder.getFile();
      if (file instanceof PsiJavaFile) {
        PsiJavaFile javaFile = (PsiJavaFile)file;
        if (javaFile.getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_9)) {
          PsiJavaModule psiModule = JavaModuleGraphUtil.findDescriptorByElement(file);
          if (psiModule != null) {
            VirtualFile vFile = file.getVirtualFile();
            if (vFile != null) {
              Module module = ProjectFileIndex.SERVICE.getInstance(holder.getProject()).getModuleForFile(vFile);
              if (module != null) {
                Set<String> exportedPackageNames =
                  new THashSet<>(ContainerUtil.mapNotNull(psiModule.getExports(), PsiPackageAccessibilityStatement::getPackageName));
                if (exportedPackageNames.contains(javaFile.getPackageName())) {
                  checkers.add(new Java9NonAccessibleTypeExposedVisitor(holder, module, psiModule.getName(), exportedPackageNames));
                }
              }
            }
          }
        }
      }
    }
    if (checkPublicApi || checkPackageLocal) {
      checkers.add(new ClassEscapesItsScopeVisitor(holder));
    }
    return !checkers.isEmpty() ? new VisibilityVisitor(checkers.toArray(VisibilityChecker.EMPTY_ARRAY)) : PsiElementVisitor.EMPTY_VISITOR;
  }

  private static class VisibilityVisitor extends JavaElementVisitor {
    private final VisibilityChecker[] myCheckers;

    VisibilityVisitor(VisibilityChecker[] checkers) {
      myCheckers = checkers;
    }

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      PsiElement parent = reference.getParent();
      if (parent instanceof PsiTypeElement || parent instanceof PsiReferenceList) {
        PsiElement grandParent = PsiTreeUtil.skipParentsOfType(reference, PsiTypeElement.class, PsiReferenceList.class,
                                                               PsiParameter.class, PsiParameterList.class,
                                                               PsiReferenceParameterList.class, PsiJavaCodeReferenceElement.class,
                                                               PsiTypeParameter.class, PsiTypeParameterList.class);
        if (grandParent instanceof PsiField || grandParent instanceof PsiMethod) {
          PsiMember member = (PsiMember)grandParent;
          if (!isPrivate(member)) {
            PsiElement resolved = reference.resolve();
            if (resolved instanceof PsiClass && !(resolved instanceof PsiTypeParameter)) {
              PsiClass psiClass = (PsiClass)resolved;
              for (VisibilityChecker checker : myCheckers) {
                if (checker.checkVisibilityIssue(member, psiClass, reference)) {
                  return;
                }
              }
            }
          }
        }
      }
    }

    private static boolean isPrivate(@NotNull PsiMember member) {
      if (member.hasModifierProperty(PsiModifier.PRIVATE)) {
        return true;
      }
      PsiClass containingClass = member.getContainingClass();
      if (containingClass != null && isPrivate(containingClass)) {
        return true;
      }
      return false;
    }
  }

  private static abstract class VisibilityChecker {
    static final VisibilityChecker[] EMPTY_ARRAY = new VisibilityChecker[0];
    final ProblemsHolder myHolder;

    protected VisibilityChecker(ProblemsHolder holder) {
      myHolder = holder;
    }

    abstract boolean checkVisibilityIssue(PsiMember member, PsiClass psiClass, PsiJavaCodeReferenceElement reference);
  }

  private class ClassEscapesItsScopeVisitor extends VisibilityChecker {
    ClassEscapesItsScopeVisitor(ProblemsHolder holder) {
      super(holder);
    }

    @Override
    boolean checkVisibilityIssue(PsiMember member, PsiClass psiClass, PsiJavaCodeReferenceElement reference) {
      if (needToCheck(member) && isLessRestrictiveScope(member, psiClass)) {
        myHolder.registerProblem(reference, InspectionGadgetsBundle.message("class.escapes.defined.scope.problem.descriptor"));
        return true;
      }
      return false;
    }

    private boolean needToCheck(PsiMember member) {
      return checkPublicApi && (member.hasModifierProperty(PsiModifier.PUBLIC) || member.hasModifierProperty(PsiModifier.PROTECTED)) ||
             checkPackageLocal && member.hasModifierProperty(PsiModifier.PACKAGE_LOCAL);
    }

    private boolean isLessRestrictiveScope(@NotNull PsiMember member, @NotNull PsiClass aClass) {
      final int methodScopeOrder = getScopeOrder(member);
      final int classScopeOrder = getScopeOrder(aClass);
      final PsiClass containingClass = member.getContainingClass();
      if (containingClass == null ||
          containingClass.getQualifiedName() == null) {
        return false;
      }
      final int containingClassScopeOrder = getScopeOrder(containingClass);
      return methodScopeOrder > classScopeOrder && containingClassScopeOrder > classScopeOrder;
    }

    private int getScopeOrder(@NotNull PsiModifierListOwner element) {
      if (element.hasModifierProperty(PsiModifier.PUBLIC)) {
        return 4;
      }
      else if (element.hasModifierProperty(PsiModifier.PRIVATE)) {
        return 1;
      }
      else if (element.hasModifierProperty(PsiModifier.PROTECTED)) {
        return 3;
      }
      else {
        return 2;
      }
    }
  }

  private static class Java9NonAccessibleTypeExposedVisitor extends VisibilityChecker {
    private final ModuleFileIndex myModuleFileIndex;
    private final Set<String> myExportedPackageNames;
    private final String myModuleName;

    Java9NonAccessibleTypeExposedVisitor(@NotNull ProblemsHolder holder,
                                                @NotNull Module module,
                                                @NotNull String moduleName,
                                                @NotNull Set<String> exportedPackageNames) {
      super(holder);
      myModuleName = moduleName;
      myModuleFileIndex = ModuleRootManager.getInstance(module).getFileIndex();
      myExportedPackageNames = exportedPackageNames;
    }

    @Override
    public boolean checkVisibilityIssue(PsiMember member, PsiClass psiClass, PsiJavaCodeReferenceElement reference) {
      if (isModulePublicApi(member) && !isModulePublicApi(psiClass) && isInModuleSource(psiClass)) {
        myHolder.registerProblem(reference,
                                 InspectionGadgetsBundle.message("class.escapes.defined.scope.java9.modules.descriptor", myModuleName));
        return true;
      }
      return false;
    }

    private static boolean isInFinalClass(@NotNull PsiMember member) {
      final PsiClass containingClass = member.getContainingClass();
      if (containingClass == null) return false;
      return containingClass.hasModifierProperty(PsiModifier.FINAL);
    }

    @Contract("null -> false")
    private boolean isModulePublicApi(@Nullable PsiMember member) {
      if (member == null || member instanceof PsiTypeParameter) return false;
      if (member.hasModifierProperty(PsiModifier.PUBLIC) || !isInFinalClass(member) && member.hasModifierProperty(PsiModifier.PROTECTED)) {
        PsiElement parent = member.getParent();
        if (parent instanceof PsiClass) {
          return isModulePublicApi((PsiClass)parent);
        }
        if (parent instanceof PsiJavaFile) {
          String packageName = ((PsiJavaFile)parent).getPackageName();
          return myExportedPackageNames.contains(packageName);
        }
      }
      return false;
    }

    private boolean isInModuleSource(@NotNull PsiClass psiClass) {
      PsiFile psiFile = psiClass.getContainingFile();
      if (psiFile != null) {
        VirtualFile vFile = psiFile.getVirtualFile();
        if (vFile != null) {
          return myModuleFileIndex.isInSourceContent(vFile);
        }
      }
      return false;
    }
  }
}