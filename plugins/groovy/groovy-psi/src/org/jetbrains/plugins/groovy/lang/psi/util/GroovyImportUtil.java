// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
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

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyUnusedImportUtil.unusedImports;

public final class GroovyImportUtil {
  public static void processFile(final @NotNull GroovyFile file,
                                 final @NotNull Set<? super String> importedClasses,
                                 final @NotNull Set<? super String> staticallyImportedMembers,
                                 final @NotNull Set<? super GrImportStatement> usedImports,
                                 final @NotNull Set<? super GrImportStatement> unresolvedOnDemandImports,
                                 final @NotNull Set<? super String> implicitlyImported,
                                 final @NotNull Set<? super String> innerClasses,
                                 final @NotNull Map<String, String> aliased,
                                 final @NotNull Map<String, String> annotations) {
    final Set<String> unresolvedReferenceNames = new LinkedHashSet<>();

    file.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (!(element instanceof GrImportStatement) && !(element instanceof GrPackageDefinition)) {
          super.visitElement(element);
        }
        if (element instanceof GrReferenceElement) {
          visitRefElement((GrReferenceElement)element);
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
              else {
                importedClasses.add(importedName);
                if (resolved instanceof PsiClass && ((PsiClass)resolved).getContainingClass() != null) {
                  innerClasses.add(importedName);
                }
              }
            }
          }
          else if (context == null && !(refElement.getParent() instanceof GrImportStatement) && refElement.getQualifier() == null &&
                   (!(resolved instanceof PsiClass) || ((PsiClass)resolved).getContainingClass() == null)) {
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

    final Set<GrImportStatement> importsToCheck = new LinkedHashSet<>(PsiUtil.getValidImportStatements(file));
    for (GrImportStatement anImport : importsToCheck) {
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
        final String annotationText = importStatement.getAnnotationList().getText();
        if (!StringUtil.isEmptyOrSpaces(annotationText)) {
          final String importRef = importStatement.getImportFqn();
          annotations.put(importRef, annotationText);
        }
      }
    });
    usedImports.removeAll(unusedImports(file));
  }

  private static @Nullable String getTargetQualifiedName(PsiElement element) {
    if (element instanceof PsiClass) {
      return ((PsiClass)element).getQualifiedName();
    }
    if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
      PsiClass aClass = ((PsiMethod)element).getContainingClass();
      if (aClass != null) {
        return aClass.getQualifiedName();
      }
    }
    return null;
  }

  public static boolean isAnnotatedImport(GrImportStatement anImport) {
    return !StringUtil.isEmptyOrSpaces(anImport.getAnnotationList().getText());
  }
}
