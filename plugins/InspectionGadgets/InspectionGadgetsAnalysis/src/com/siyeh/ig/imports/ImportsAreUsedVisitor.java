/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.imports;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class ImportsAreUsedVisitor extends JavaRecursiveElementVisitor {

  private final PsiJavaFile myFile;
  private final List<PsiImportStatementBase> importStatements;
  private final List<PsiImportStatementBase> usedImportStatements = new ArrayList();

  ImportsAreUsedVisitor(PsiJavaFile file) {
    myFile = file;
    final PsiImportList importList = file.getImportList();
    if (importList == null) {
      importStatements = Collections.EMPTY_LIST;
    } else {
      final PsiImportStatementBase[] importStatements = importList.getAllImportStatements();
      this.importStatements = new ArrayList(Arrays.asList(importStatements));
      Collections.reverse(this.importStatements);
    }
  }

  @Override
  public void visitElement(PsiElement element) {
    if (importStatements.isEmpty()) {
      return;
    }
    super.visitElement(element);
  }

  @Override
  public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
    followReferenceToImport(reference);
    super.visitReferenceElement(reference);
  }

  private void followReferenceToImport(PsiJavaCodeReferenceElement reference) {
    if (reference.getQualifier() != null) {
      // it's already fully qualified, so the import statement wasn't
      // responsible
      return;
    }
    // during typing there can be incomplete code
    final JavaResolveResult resolveResult = reference.advancedResolve(true);
    final PsiElement element = resolveResult.getElement();
    if (element == null) {
      return;
    }
    if (findImport(element, usedImportStatements) != null) {
      return;
    }
    final PsiImportStatementBase foundImport = findImport(element, importStatements);
    if (foundImport != null) {
      importStatements.remove(foundImport);
      usedImportStatements.add(foundImport);
    }
  }

  private PsiImportStatementBase findImport(PsiElement element, List<PsiImportStatementBase> importStatements) {
    final String qualifiedName;
    final String packageName;
    if (element instanceof PsiClass) {
      final PsiClass referencedClass = (PsiClass)element;
      qualifiedName = referencedClass.getQualifiedName();
      packageName = qualifiedName != null ? StringUtil.getPackageName(qualifiedName) : null;
    }
    else {
      qualifiedName = null;
      packageName = null;
    }
    final PsiClass referenceClass;
    final String referenceName;
    if (element instanceof PsiMember) {
      final PsiMember member = (PsiMember)element;
      if (member instanceof PsiClass && !member.hasModifierProperty(PsiModifier.STATIC)) {
        referenceClass = null;
        referenceName = null;
      }
      else {
        referenceClass = member.getContainingClass();
        referenceName = member.getName();
      }
    }
    else {
      referenceClass = null;
      referenceName = null;
    }
    final boolean hasOnDemandImportConflict = qualifiedName != null && ImportUtils.hasOnDemandImportConflict(qualifiedName, myFile);
    for (PsiImportStatementBase importStatementBase : importStatements) {
      if (importStatementBase instanceof PsiImportStatement && qualifiedName != null && packageName != null) {
        final PsiImportStatement importStatement = (PsiImportStatement)importStatementBase;
        final String importName = importStatement.getQualifiedName();
        if (importName != null) {
          if (importStatement.isOnDemand()) {
            if (hasOnDemandImportConflict) {
              continue;
            }
            if (importName.equals(packageName)) {
              return importStatement;
            }
          }
          else if (importName.equals(qualifiedName)) {
            return importStatement;
          }
        }
      }
      if (importStatementBase instanceof PsiImportStaticStatement && referenceClass != null && referenceName != null) {
        final PsiImportStaticStatement importStaticStatement = (PsiImportStaticStatement)importStatementBase;
        if (importStaticStatement.isOnDemand()) {
          final PsiClass targetClass = importStaticStatement.resolveTargetClass();
          if (InheritanceUtil.isInheritorOrSelf(targetClass, referenceClass, true)) {
            return importStaticStatement;
          }
        }
        else {
          final String importReferenceName = importStaticStatement.getReferenceName();
          if (importReferenceName != null) {
            if (importReferenceName.equals(referenceName)) {
              return importStaticStatement;
            }
          }
        }
      }
    }
    return null;
  }

  public PsiImportStatementBase[] getUnusedImportStatements() {
    if (importStatements.isEmpty()) {
      return PsiImportStatementBase.EMPTY_ARRAY;
    }
    return importStatements.toArray(new PsiImportStatementBase[importStatements.size()]);
  }
}