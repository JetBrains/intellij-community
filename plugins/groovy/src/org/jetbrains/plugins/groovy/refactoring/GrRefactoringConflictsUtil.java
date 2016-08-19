/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Collection;
import java.util.List;
import java.util.Set;


/**
 * Created by Max Medvedev on 9/28/13
 */
public class GrRefactoringConflictsUtil {
  private GrRefactoringConflictsUtil() { }

  public static void analyzeAccessibilityConflicts(@NotNull Set<GrMember> membersToMove,
                                                   @NotNull PsiClass targetClass,
                                                   @NotNull MultiMap<PsiElement, String> conflicts,
                                                   @Nullable String newVisibility) {
    analyzeAccessibilityConflicts(membersToMove, targetClass, conflicts, newVisibility, targetClass, null);
  }

  public static void analyzeAccessibilityConflicts(@NotNull Set<GrMember> membersToMove,
                                                   @Nullable PsiClass targetClass,
                                                   @NotNull MultiMap<PsiElement, String> conflicts,
                                                   @Nullable String newVisibility,
                                                   @NotNull PsiElement context,
                                                   @Nullable Set<PsiMethod> abstractMethods) {
    if (VisibilityUtil.ESCALATE_VISIBILITY.equals(newVisibility)) { //Still need to check for access object
      newVisibility = PsiModifier.PUBLIC;
    }

    for (GrMember member : membersToMove) {
      checkUsedElements(member, member, membersToMove, abstractMethods, targetClass, context, conflicts);
      RefactoringConflictsUtil.checkAccessibilityConflicts(member, newVisibility, targetClass, membersToMove, conflicts);
    }
  }

  public static void checkUsedElements(PsiMember member,
                                       PsiElement scope,
                                       @NotNull Set<GrMember> membersToMove,
                                       @Nullable Set<PsiMethod> abstractMethods,
                                       @Nullable PsiClass targetClass,
                                       @NotNull PsiElement context,
                                       MultiMap<PsiElement, String> conflicts) {
    final Set<PsiMember> moving = new HashSet<>(membersToMove);
    if (abstractMethods != null) {
      moving.addAll(abstractMethods);
    }
    if (scope instanceof GrReferenceExpression) {
      GrReferenceExpression refExpr = (GrReferenceExpression)scope;
      PsiElement refElement = refExpr.resolve();
      if (refElement instanceof PsiMember) {
        if (!RefactoringHierarchyUtil.willBeInTargetClass(refElement, moving, targetClass, false)) {
          GrExpression qualifier = refExpr.getQualifierExpression();
          PsiClass accessClass = (PsiClass)(qualifier != null ? PsiUtil.getAccessObjectClass(
            qualifier).getElement() : null);
          RefactoringConflictsUtil.checkAccessibility((PsiMember)refElement, context, accessClass, member, conflicts);
        }
      }
    }
    else if (scope instanceof GrNewExpression) {
      final GrNewExpression newExpression = (GrNewExpression)scope;
      final GrAnonymousClassDefinition anonymousClass = newExpression.getAnonymousClassDefinition();
      if (anonymousClass != null) {
        if (!RefactoringHierarchyUtil.willBeInTargetClass(anonymousClass, moving, targetClass, false)) {
          RefactoringConflictsUtil.checkAccessibility(anonymousClass, context, anonymousClass, member, conflicts);
        }
      }
      else {
        final PsiMethod refElement = newExpression.resolveMethod();
        if (refElement != null) {
          if (!RefactoringHierarchyUtil.willBeInTargetClass(refElement, moving, targetClass, false)) {
            RefactoringConflictsUtil.checkAccessibility(refElement, context, null, member, conflicts);
          }
        }
      }
    }
    else if (scope instanceof GrCodeReferenceElement) {
      GrCodeReferenceElement refExpr = (GrCodeReferenceElement)scope;
      PsiElement refElement = refExpr.resolve();
      if (refElement instanceof PsiMember) {
        if (!RefactoringHierarchyUtil.willBeInTargetClass(refElement, moving, targetClass, false)) {
          RefactoringConflictsUtil.checkAccessibility((PsiMember)refElement, context, null, member, conflicts);
        }
      }
    }

    for (PsiElement child : scope.getChildren()) {
      if (child instanceof PsiWhiteSpace || child instanceof PsiComment) continue;
      checkUsedElements(member, child, membersToMove, abstractMethods, targetClass, context, conflicts);
    }
  }



  public static void analyzeModuleConflicts(final Project project,
                                            final Collection<? extends PsiElement> scopes,
                                            final UsageInfo[] usages,
                                            final PsiElement target,
                                            final MultiMap<PsiElement,String> conflicts) {
    if (scopes == null) return;
    final VirtualFile vFile = PsiUtilCore.getVirtualFile(target);
    if (vFile == null) return;


    List<GroovyPsiElement> groovyScopes =
      ContainerUtil.collect(scopes.iterator(), new FilteringIterator.InstanceOf<>(GroovyPsiElement.class));
    analyzeModuleConflicts(project, groovyScopes, usages, vFile, conflicts);
    scopes.removeAll(groovyScopes);
    RefactoringConflictsUtil.analyzeModuleConflicts(project, scopes, usages, vFile, conflicts);
  }

  public static void analyzeModuleConflicts(final Project project,
                                            final Collection<? extends GroovyPsiElement> scopes,
                                            final UsageInfo[] usages,
                                            final VirtualFile vFile,
                                            final MultiMap<PsiElement, String> conflicts) {
    if (scopes == null) return;
    for (final PsiElement scope : scopes) {
      if (scope instanceof PsiPackage) return;
    }

    final Module targetModule = ModuleUtilCore.findModuleForFile(vFile, project);
    if (targetModule == null) return;
    final GlobalSearchScope resolveScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(targetModule);
    final HashSet<PsiElement> reported = new HashSet<>();
    for (final GroovyPsiElement scope : scopes) {
      scope.accept(new GroovyRecursiveElementVisitor() {
        @Override
        public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
          super.visitCodeReferenceElement(refElement);
          visit(refElement);
        }

        @Override
        public void visitReferenceExpression(GrReferenceExpression reference) {
          super.visitReferenceExpression(reference);
          visit(reference);
        }

        private void visit(GrReferenceElement<? extends GroovyPsiElement> reference) {
          final PsiElement resolved = reference.resolve();
          if (resolved != null &&
              !reported.contains(resolved) &&
              !CommonRefactoringUtil.isAncestor(resolved, scopes) &&
              !PsiSearchScopeUtil.isInScope(resolveScope, resolved) &&
              !(resolved instanceof LightElement)) {
            final String scopeDescription = RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(reference), true);
            final String message = RefactoringBundle.message("0.referenced.in.1.will.not.be.accessible.in.module.2",
                                                             RefactoringUIUtil.getDescription(resolved, true),
                                                             scopeDescription,
                                                             CommonRefactoringUtil.htmlEmphasize(targetModule.getName()));
            conflicts.putValue(resolved, CommonRefactoringUtil.capitalize(message));
            reported.add(resolved);
          }
        }
      });
    }

    boolean isInTestSources = ModuleRootManager.getInstance(targetModule).getFileIndex().isInTestSourceContent(vFile);
    NextUsage:
    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      if (element != null && PsiTreeUtil.getParentOfType(element, GrImportStatement.class, false) == null) {

        for (PsiElement scope : scopes) {
          if (PsiTreeUtil.isAncestor(scope, element, false)) continue NextUsage;
        }

        final GlobalSearchScope resolveScope1 = element.getResolveScope();
        if (!resolveScope1.isSearchInModuleContent(targetModule, isInTestSources)) {
          final PsiFile usageFile = element.getContainingFile();
          PsiElement container;
          if (usageFile instanceof PsiJavaFile) {
            container = ConflictsUtil.getContainer(element);
          }
          else {
            container = usageFile;
          }
          final String scopeDescription = RefactoringUIUtil.getDescription(container, true);
          final VirtualFile usageVFile = usageFile.getVirtualFile();
          if (usageVFile != null) {
            Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(usageVFile);
            if (module != null) {
              final String message;
              final PsiElement referencedElement;
              if (usage instanceof MoveRenameUsageInfo) {
                referencedElement = ((MoveRenameUsageInfo)usage).getReferencedElement();
              }
              else {
                referencedElement = usage.getElement();
              }
              assert referencedElement != null : usage;
              if (module == targetModule && isInTestSources) {
                message = RefactoringBundle.message("0.referenced.in.1.will.not.be.accessible.from.production.of.module.2",
                                                    RefactoringUIUtil.getDescription(referencedElement, true),
                                                    scopeDescription,
                                                    CommonRefactoringUtil.htmlEmphasize(module.getName()));
              }
              else {
                message = RefactoringBundle.message("0.referenced.in.1.will.not.be.accessible.from.module.2",
                                                    RefactoringUIUtil.getDescription(referencedElement, true),
                                                    scopeDescription,
                                                    CommonRefactoringUtil.htmlEmphasize(module.getName()));
              }
              conflicts.putValue(referencedElement, CommonRefactoringUtil.capitalize(message));
            }
          }
        }
      }
    }
  }

}
