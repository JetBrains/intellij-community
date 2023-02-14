/*
 * Copyright 2007-2015 Bas Leijdekkers
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
package com.siyeh.ig.errorhandling;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DeleteCatchSectionFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class CaughtExceptionImmediatelyRethrownInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("caught.exception.immediately.rethrown.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiTryStatement tryStatement = (PsiTryStatement)infos[0];
    final boolean removeTryCatch = tryStatement.getCatchSections().length == 1 && tryStatement.getFinallyBlock() == null &&
      tryStatement.getResourceList() == null;
    return new DeleteCatchSectionFix(removeTryCatch);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CaughtExceptionImmediatelyRethrownVisitor();
  }

  private static class CaughtExceptionImmediatelyRethrownVisitor extends BaseInspectionVisitor {

    @Override
    public void visitThrowStatement(@NotNull PsiThrowStatement statement) {
      super.visitThrowStatement(statement);
      final PsiExpression expression = PsiUtil.skipParenthesizedExprDown(statement.getException());
      if (!(expression instanceof PsiReferenceExpression referenceExpression)) {
        return;
      }
      final PsiStatement previousStatement = PsiTreeUtil.getPrevSiblingOfType(statement, PsiStatement.class);
      if (previousStatement != null) {
        return;
      }
      final PsiElement parent = statement.getParent();
      if (parent instanceof PsiStatement) {
        // e.g. if (notsure) throw e;
        return;
      }
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiParameter parameter)) {
        return;
      }
      final PsiElement declarationScope = parameter.getDeclarationScope();
      if (!(declarationScope instanceof PsiCatchSection catchSection)) {
        return;
      }
      final PsiCodeBlock block = PsiTreeUtil.getParentOfType(statement, PsiCodeBlock.class);
      if (block == null) {
        return;
      }
      final PsiElement blockParent = block.getParent();
      if (blockParent != catchSection) {
        // e.g. if (notsure) { throw e; }
        return;
      }
      if (isSuperClassExceptionCaughtLater(parameter, catchSection)) {
        return;
      }
      final Query<PsiReference> query = ReferencesSearch.search(parameter);
      for (PsiReference reference : query) {
        final PsiElement element = reference.getElement();
        if (element != expression) {
          return;
        }
      }
      final PsiTryStatement tryStatement = catchSection.getTryStatement();
      registerVariableError(parameter, tryStatement);
    }

    private static boolean isSuperClassExceptionCaughtLater(PsiVariable parameter, PsiCatchSection catchSection) {
      final PsiTryStatement tryStatement = catchSection.getTryStatement();
      final PsiCatchSection[] catchSections = tryStatement.getCatchSections();
      int index = 0;
      while (catchSections[index] != catchSection) {
        index++;
      }
      final PsiType type = parameter.getType();
      final Set<PsiClass> parameterClasses = new HashSet<>();
      processExceptionClasses(type, aClass -> {
        parameterClasses.add(aClass);
        return true;
      });
      if (parameterClasses.isEmpty()) {
        return false;
      }
      final Ref<Boolean> superClassExceptionType = new Ref<>(Boolean.FALSE);
      for (int i = index; i < catchSections.length; i++) {
        final PsiCatchSection nextCatchSection = catchSections[i];
        final PsiParameter nextParameter = nextCatchSection.getParameter();
        if (nextParameter == null) {
          continue;
        }
        final PsiType nextType = nextParameter.getType();
        processExceptionClasses(nextType, aClass -> {
          for (PsiClass parameterClass : parameterClasses) {
            if (parameterClass.isInheritor(aClass, true)) {
              superClassExceptionType.set(Boolean.TRUE);
              return false;
            }
          }
          return true;
        });
        if (superClassExceptionType.get().booleanValue()) {
          return true;
        }
      }
      return false;
    }

    private static void processExceptionClasses(PsiType type, Processor<? super PsiClass> processor) {
      if (type instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType)type;
        final PsiClass aClass = classType.resolve();
        if (aClass != null) {
          processor.process(aClass);
        }
      }
      else if (type instanceof PsiDisjunctionType disjunctionType) {
        for (PsiType disjunction : disjunctionType.getDisjunctions()) {
          if (!(disjunction instanceof PsiClassType classType)) {
            continue;
          }
          final PsiClass aClass = classType.resolve();
          if (aClass != null) {
            processor.process(aClass);
          }
        }
      }
    }
  }
}