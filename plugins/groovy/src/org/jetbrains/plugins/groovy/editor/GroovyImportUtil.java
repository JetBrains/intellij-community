// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.editor;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyImportHelper;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyUnusedImportUtil;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class GroovyImportUtil {
  static void processFile(@NotNull GroovyFile file,
                          @NotNull Set<String> importedModules,
                          @NotNull Set<String> importedClasses,
                          @NotNull Set<String> staticallyImportedMembers,
                          @NotNull Set<GrImportStatement> usedImports,
                          @NotNull Set<GrImportStatement> unresolvedOnDemandImports,
                          @NotNull Set<String> implicitlyImported,
                          @NotNull Set<String> innerClasses,
                          @NotNull Map<String, String> aliased,
                          @NotNull Map<String, String> annotations) {
    final Set<String> unresolvedReferenceNames = new LinkedHashSet<>();

    file.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (!(element instanceof GrImportStatement) && !(element instanceof GrPackageDefinition)) {
          super.visitElement(element);
        }
        if (element instanceof GrReferenceElement ref) {
          visitRefElement(ref);
        }
      }

      private void visitRefElement(GrReferenceElement refElement) {
        if (refElement.isQualified()) return;

        final String refName = refElement.getReferenceName();
        if ("super".equals(refName)) return;

        final GroovyResolveResult[] resolveResults = refElement.multiResolve(false);
        if (resolveResults.length == 0 && refName != null) {
          if (PsiTreeUtil.getParentOfType(refElement, GrImportStatement.class) == null) {
            unresolvedReferenceNames.add(refName);
          }
        }

        for (GroovyResolveResult resolveResult : resolveResults) {
          final PsiElement context = resolveResult.getCurrentFileResolveContext();
          final PsiElement resolved = resolveResult.getElement();
          if (resolved == null) return;

          if (context instanceof GrImportStatement importStatement) {
            usedImports.add(importStatement);
            if (GroovyImportHelper.isImplicitlyImported(resolved, refName, file)) {
              addImplicitClass(resolved);
            }

            if (!importStatement.isAliasedImport() && !isAnnotatedImport(importStatement)) {
              String importedName = null;
              if (importStatement.isOnDemand()) {
                if (importStatement.isStatic()) {
                  if (resolved instanceof PsiMember member) {
                    final PsiClass clazz = member.getContainingClass();
                    if (clazz != null) {
                      final String classQName = clazz.getQualifiedName();
                      if (classQName != null) {
                        final String name = member.getName();
                        if (name != null) {
                          importedName = classQName + "." + name;
                        }
                      }
                    }
                  }
                }
                else {
                  importedName = getTargetQualifiedName(resolved);
                }
              }
              else {
                importedName = importStatement.getImportFqn();
              }

              if (importedName == null) return;

              final String importRef = importStatement.getImportFqn();
              if (importStatement.isAliasedImport()) {
                aliased.put(importRef, importedName);
                return;
              }

              if (importStatement.isStatic()) {
                staticallyImportedMembers.add(importedName);
              }
              else if (importStatement.isModule()) {
                importedModules.add(importedName);
              }
              else {
                importedClasses.add(importedName);
                if (resolved instanceof PsiClass aClass && aClass.getContainingClass() != null) {
                  innerClasses.add(importedName);
                }
              }
            }
          }
          else if (context == null && !(refElement.getParent() instanceof GrImportStatement) && refElement.getQualifier() == null &&
                   (!(resolved instanceof PsiClass aClass) || aClass.getContainingClass() == null)) {
            addImplicitClass(resolved);
          }
        }
      }

      private void addImplicitClass(PsiElement element) {
        final String qname = getTargetQualifiedName(element);
        if (qname != null) {
          implicitlyImported.add(qname);
          importedClasses.add(qname);
        }
      }
    });

    for (GrImportStatement anImport : PsiUtil.getValidImportStatements(file)) {
      if (usedImports.contains(anImport)) continue;

      final GrCodeReferenceElement ref = anImport.getImportReference();
      assert ref != null : "invalid import!";

      if (ref.resolve() == null) {
        if (anImport.isOnDemand()) {
          usedImports.add(anImport);
          unresolvedOnDemandImports.add(anImport);
        }
        else {
          String importedName = anImport.getImportedName();
          if (importedName != null && unresolvedReferenceNames.contains(importedName)) {
            usedImports.add(anImport);

            final String symbolName = anImport.getImportFqn();
            if (anImport.isAliasedImport()) {
              aliased.put(symbolName, importedName);
            }
            else if (anImport.isStatic()) {
              staticallyImportedMembers.add(symbolName);
            }
            else if (!isAnnotatedImport(anImport)) {
              importedClasses.add(symbolName);
            }
          }
        }
      }
    }

    file.acceptChildren(new GroovyElementVisitor() {
      @Override
      public void visitImportStatement(@NotNull GrImportStatement importStatement) {
        if (isAnnotatedImport(importStatement)) {
          annotations.put(importStatement.getImportFqn(), importStatement.getAnnotationList().getText());
        }
      }
    });
    usedImports.removeAll(GroovyUnusedImportUtil.unusedImports(file));
  }

  private static @Nullable String getTargetQualifiedName(PsiElement element) {
    if (element instanceof PsiClass aClass) {
      return aClass.getQualifiedName();
    }
    if (element instanceof PsiMethod method && method.isConstructor()) {
      PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
        return aClass.getQualifiedName();
      }
    }
    return null;
  }

  public static boolean isAnnotatedImport(GrImportStatement anImport) {
    return anImport.getAnnotationList().getFirstChild() != null;
  }
}
