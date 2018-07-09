/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
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

import java.util.Map;
import java.util.Set;

import static org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyUnusedImportUtil.unusedImports;

public class GroovyImportUtil {
  public static void processFile(@Nullable final PsiFile file,
                                 @Nullable final Set<String> importedClasses,
                                 @Nullable final Set<String> staticallyImportedMembers,
                                 @Nullable final Set<GrImportStatement> usedImports,
                                 @Nullable final Set<GrImportStatement> unresolvedOnDemandImports,
                                 @Nullable final Set<String> implicitlyImported,
                                 @Nullable final Set<String> innerClasses,
                                 @Nullable final Map<String, String> aliased,
                                 @Nullable final Map<String, String> annotations) {
    if (!(file instanceof GroovyFile)) return;

    final Set<String> unresolvedReferenceNames = ContainerUtil.newLinkedHashSet();

    file.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
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

          if (context instanceof GrImportStatement) {
            final GrImportStatement importStatement = (GrImportStatement)context;

            if (usedImports != null) {
              usedImports.add(importStatement);
            }
            if (GroovyImportHelper.isImplicitlyImported(resolved, refName, (GroovyFile)file)) {
              addImplicitClass(resolved);
            }

            if (!importStatement.isAliasedImport() && !isAnnotatedImport(importStatement)) {
              String importedName = null;
              if (importStatement.isOnDemand()) {

                if (importStatement.isStatic()) {
                  if (resolved instanceof PsiMember) {
                    final PsiMember member = (PsiMember)resolved;
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
                final GrCodeReferenceElement importReference = importStatement.getImportReference();
                if (importReference != null) {
                  importedName = PsiUtil.getQualifiedReferenceText(importReference);
                }
              }

              if (importedName == null) return;

              final String importRef = getImportReferenceText(importStatement);

              if (importStatement.isAliasedImport()) {
                if (aliased != null) {
                  aliased.put(importRef, importedName);
                }
                return;
              }

              if (importStatement.isStatic()) {
                if (staticallyImportedMembers != null) {
                  staticallyImportedMembers.add(importedName);
                }
              }
              else {
                if (importedClasses != null) {
                  importedClasses.add(importedName);
                }
                if (resolved instanceof PsiClass && ((PsiClass)resolved).getContainingClass() != null && innerClasses != null) {
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
          if (implicitlyImported != null) {
            implicitlyImported.add(qname);
          }
          if (importedClasses != null) {
            importedClasses.add(qname);
          }
        }
      }
    });

    final Set<GrImportStatement> importsToCheck = ContainerUtil.newLinkedHashSet(PsiUtil.getValidImportStatements((GroovyFile)file));
    for (GrImportStatement anImport : importsToCheck) {
      if (usedImports != null && usedImports.contains(anImport)) continue;

      final GrCodeReferenceElement ref = anImport.getImportReference();
      assert ref != null : "invalid import!";

      if (ref.resolve() == null) {
        if (anImport.isOnDemand()) {
          if (usedImports != null) {
            usedImports.add(anImport);
          }
          if (unresolvedOnDemandImports != null) {
            unresolvedOnDemandImports.add(anImport);
          }
        }
        else {
          String importedName = anImport.getImportedName();
          if (importedName != null && unresolvedReferenceNames.contains(importedName)) {
            if (usedImports != null) {
              usedImports.add(anImport);
            }

            final String symbolName = getImportReferenceText(anImport);

            if (anImport.isAliasedImport()) {
              if (aliased != null) {
                aliased.put(symbolName, importedName);
              }
            }
            else {
              if (anImport.isStatic()) {
                if (staticallyImportedMembers != null) {
                  staticallyImportedMembers.add(symbolName);
                }
              }
              else {
                if (importedClasses != null) {
                  importedClasses.add(symbolName);
                }
              }
            }
          }
        }
      }
    }

    if (annotations != null) {
      ((GroovyFile)file).acceptChildren(new GroovyElementVisitor() {
        @Override
        public void visitImportStatement(@NotNull GrImportStatement importStatement) {
          final String annotationText = importStatement.getAnnotationList().getText();
          if (!StringUtil.isEmptyOrSpaces(annotationText)) {
            final String importRef = getImportReferenceText(importStatement);
            annotations.put(importRef, annotationText);
          }
        }
      });
    }
    if (usedImports != null) {
      usedImports.removeAll(unusedImports((GroovyFile)file));
    }
  }

  @Nullable
  private static String getTargetQualifiedName(PsiElement element) {
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

  @Nullable
  public static String getImportReferenceText(GrImportStatement statement) {
    GrCodeReferenceElement importReference = statement.getImportReference();
    if (importReference != null) {
      return importReference.getClassNameText();
    }
    return null;
  }
}
