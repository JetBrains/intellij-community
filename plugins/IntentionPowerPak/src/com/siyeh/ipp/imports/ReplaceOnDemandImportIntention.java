/*
 * Copyright 2006-2018 Bas Leijdekkers
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
package com.siyeh.ipp.imports;

import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.ImportsUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

public class ReplaceOnDemandImportIntention extends Intention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new OnDemandImportPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiImportStatementBase importStatementBase = (PsiImportStatementBase)element;
    final PsiJavaFile javaFile = (PsiJavaFile)importStatementBase.getContainingFile();
    final PsiManager manager = importStatementBase.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    if (importStatementBase instanceof PsiImportStatement) {
      final PsiImportStatement importStatement = (PsiImportStatement)importStatementBase;
      final PsiClass[] classes = javaFile.getClasses();
      final String qualifiedName = importStatement.getQualifiedName();
      final ClassCollector visitor = new ClassCollector(qualifiedName);
      for (PsiClass aClass : classes) {
        aClass.accept(visitor);
      }
      final PsiClass[] importedClasses = visitor.getImportedClasses();
      Arrays.sort(importedClasses, new PsiClassComparator());
      createImportStatements(importStatement, importedClasses, factory::createImportStatement);
    }
    else if (importStatementBase instanceof PsiImportStaticStatement) {
      PsiClass targetClass = ((PsiImportStaticStatement)importStatementBase).resolveTargetClass();
      if (targetClass != null) {
        String[] members = ImportsUtil.collectReferencesThrough(javaFile,
                                                                importStatementBase.getImportReference(),
                                                                (PsiImportStaticStatement)importStatementBase)
          .stream()
          .map(PsiReference::resolve)
          .filter(resolve -> resolve instanceof PsiMember)
          .map(member -> ((PsiMember)member).getName())
          .distinct()
          .filter(Objects::nonNull)
          .toArray(String[]::new);

        createImportStatements(importStatementBase,
                               members,
                               member -> factory.createImportStaticStatement(targetClass, member));
      }
    }
  }

  private static <T> void createImportStatements(PsiImportStatementBase importStatement,
                                                 T[] importedMembers,
                                                 Function<? super T, ? extends PsiImportStatementBase> function) {
    final PsiElement importList = importStatement.getParent();
    for (T importedMember : importedMembers) {
      importList.add(function.apply(importedMember));
    }
    new CommentTracker().deleteAndRestoreComments(importStatement);
  }

  private static class ClassCollector extends JavaRecursiveElementWalkingVisitor {

    private final String importedPackageName;
    private final Set<PsiClass> importedClasses = new HashSet<>();

    ClassCollector(String importedPackageName) {
      this.importedPackageName = importedPackageName;
    }

    @Override
    public void visitReferenceElement(
      PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      if (reference.isQualified()) {
        return;
      }
      final PsiElement element = reference.resolve();
      if (!(element instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)element;
      final String qualifiedName = aClass.getQualifiedName();
      final String packageName =
        ClassUtil.extractPackageName(qualifiedName);
      if (!importedPackageName.equals(packageName)) {
        return;
      }
      importedClasses.add(aClass);
    }

    public PsiClass[] getImportedClasses() {
      return importedClasses.toArray(PsiClass.EMPTY_ARRAY);
    }
  }

  private static final class PsiClassComparator
    implements Comparator<PsiClass> {

    @Override
    public int compare(PsiClass class1, PsiClass class2) {
      final String qualifiedName1 = class1.getQualifiedName();
      final String qualifiedName2 = class2.getQualifiedName();
      if (qualifiedName1 == null) {
        return -1;
      }
      return qualifiedName1.compareTo(qualifiedName2);
    }
  }
}