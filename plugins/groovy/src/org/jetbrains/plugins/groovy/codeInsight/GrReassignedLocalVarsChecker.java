/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInsight;

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.Collection;
import java.util.Set;

/**
 * @author Max Medvedev
 */
public class GrReassignedLocalVarsChecker {
  private static final Key<CachedValue<PsiType>> LEAST_UPPER_BOUND_TYPE = Key.create("least upper bound type");
  private static final Key<CachedValue<Set<String>>> ASSIGNED_VARS = Key.create("assigned vars inside block");
  private static final Key<CachedValue<Boolean>> REASSIGNED_VAR = Key.create("least upper bound type");

  @Nullable
  public static Boolean isReassignedVar(@NotNull final GrReferenceExpression refExpr) {
    if (!PsiUtil.isCompileStatic(refExpr)) {
      return false;
    }

    if (refExpr.getQualifier() != null) {
      return false;
    }

    final PsiElement resolved = refExpr.resolve();
    if (!GroovyRefactoringUtil.isLocalVariable(resolved)) {
      return false;
    }

    assert resolved != null;
    CachedValue<Boolean> data = resolved.getUserData(REASSIGNED_VAR);
    if (data == null) {
      data = CachedValuesManager.getManager(refExpr.getProject()).createCachedValue(new CachedValueProvider<Boolean>() {
        @Nullable
        @Override
        public Result<Boolean> compute() {
          return Result.create(isReassignedVarImpl((GrVariable)resolved), PsiModificationTracker.MODIFICATION_COUNT, ProjectRootManager.getInstance(resolved.getProject()));
        }
      }, false);
      resolved.putUserData(REASSIGNED_VAR, data);
    }
    return data.getValue();
  }

  private static boolean isReassignedVarImpl(@NotNull final GrVariable resolved) {
    final GrControlFlowOwner variableScope = PsiTreeUtil.getParentOfType(resolved, GrCodeBlock.class, GroovyFile.class);
    if (variableScope == null) return false;

    final String name = resolved.getName();
    final Ref<Boolean> isReassigned = Ref.create(false);
    for (PsiElement scope = resolved.getParent().getNextSibling(); scope != null; scope = scope.getNextSibling()) {
      if (scope instanceof GroovyPsiElement) {
        ((GroovyPsiElement)scope).accept(new GroovyRecursiveElementVisitor() {
          @Override
          public void visitClosure(GrClosableBlock closure) {
            if (getUsedVarsInsideBlock(closure).contains(name)) {
              isReassigned.set(true);
            }
          }

          @Override
          public void visitElement(GroovyPsiElement element) {
            if (isReassigned.get()) return;
            super.visitElement(element);
          }
        });

        if (isReassigned.get()) break;
      }
    }

    return isReassigned.get();
  }


  @Nullable
  public static PsiType getReassignedVarType(GrReferenceExpression refExpr, boolean honorCompileStatic) {
    if (honorCompileStatic && !PsiUtil.isCompileStatic(refExpr) || refExpr.getQualifier() != null) {
      return null;
    }

    final PsiElement resolved = refExpr.resolve();
    if (!GroovyRefactoringUtil.isLocalVariable(resolved)) {
      return null;
    }

    assert resolved != null;
    return getLeastUpperBoundByVar((GrVariable)resolved);
  }

  @Nullable
  private static PsiType getLeastUpperBoundByVar(@NotNull final GrVariable resolved) {
    CachedValue<PsiType> data = resolved.getUserData(LEAST_UPPER_BOUND_TYPE);
    if (data == null) {
      data = CachedValuesManager.getManager(resolved.getProject()).createCachedValue(new CachedValueProvider<PsiType>() {
        @Override
        public Result<PsiType> compute() {
          return Result.create(getLeastUpperBoundByVarImpl(resolved), PsiModificationTracker.MODIFICATION_COUNT, ProjectRootManager.getInstance(resolved.getProject()));
        }
      }, false);
      resolved.putUserData(LEAST_UPPER_BOUND_TYPE, data);
    }
    return data.getValue();
  }

  @Nullable
  private static PsiType getLeastUpperBoundByVarImpl(@NotNull final GrVariable resolved) {
    return RecursionManager.doPreventingRecursion(resolved, false, new NullableComputable<PsiType>() {
      @Override
      public PsiType compute() {
        final Collection<PsiReference> all = ReferencesSearch.search(resolved).findAll();

        final GrExpression initializer = resolved.getInitializerGroovy();
        PsiType result = initializer != null ? initializer.getType() : null;

        final PsiManager manager = resolved.getManager();
        for (PsiReference reference : all) {
          final PsiElement ref = reference.getElement();
          if (ref instanceof GrReferenceExpression && PsiUtil.isLValue(((GrReferenceExpression)ref))) {
            result = TypesUtil.getLeastUpperBoundNullable(result, TypeInferenceHelper.getInitializerTypeFor(ref), manager);
          }
        }
        return result;
      }
    });
  }

  @NotNull
  private static Set<String> getUsedVarsInsideBlock(@NotNull final GrCodeBlock block) {
    CachedValue<Set<String>> data = block.getUserData(ASSIGNED_VARS);

    if (data == null) {
      data = CachedValuesManager.getManager(block.getProject()).createCachedValue(new CachedValueProvider<Set<String>>() {
        @Nullable
        @Override
        public Result<Set<String>> compute() {
          final Set<String> result = ContainerUtil.newHashSet();

          block.acceptChildren(new GroovyRecursiveElementVisitor() {

            @Override
            public void visitOpenBlock(GrOpenBlock openBlock) {
              result.addAll(getUsedVarsInsideBlock(openBlock));
            }

            @Override
            public void visitClosure(GrClosableBlock closure) {
              result.addAll(getUsedVarsInsideBlock(closure));
            }

            @Override
            public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
              if (referenceExpression.getQualifier() == null && referenceExpression.getReferenceName() != null) {
                result.add(referenceExpression.getReferenceName());
              }
            }
          });
          return Result.create(result, block);
        }
      }, false);
      block.putUserData(ASSIGNED_VARS, data);
    }

    return data.getValue();
  }

}
