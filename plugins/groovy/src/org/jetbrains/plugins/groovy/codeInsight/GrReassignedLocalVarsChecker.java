/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.Collection;

/**
 * @author Max Medvedev
 */
public class GrReassignedLocalVarsChecker {
  private static final Key<CachedValue<PsiType>> LEAST_UPPER_BOUND_TYPE = Key.create("least upper bound type");

  @Nullable
  public static PsiType checkReassignedVar(GrReferenceExpression refExpr, boolean honorCompileStatic) {
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
  private static PsiType getLeastUpperBoundByVar(final GrVariable resolved) {
    CachedValue<PsiType> data = resolved.getUserData(LEAST_UPPER_BOUND_TYPE);
    if (data == null) {
      data = CachedValuesManager.getManager(resolved.getProject()).createCachedValue(new CachedValueProvider<PsiType>() {
        @Override
        public Result<PsiType> compute() {
          return Result.create(getLeastUpperBoundByVarImpl(resolved), PsiModificationTracker.MODIFICATION_COUNT, ProjectRootManager.getInstance(
            resolved.getProject()));
        }
      }, false);
    }
    return data.getValue();
  }

  @Nullable
  private static PsiType getLeastUpperBoundByVarImpl(final GrVariable resolved) {
    return RecursionManager.doPreventingRecursion(resolved, false, new NullableComputable<PsiType>() {
      @Override
      public PsiType compute() {
        final GrControlFlowOwner flowOwner = ControlFlowUtils.findControlFlowOwner(resolved);
        final Collection<PsiReference> all = ReferencesSearch.search(resolved, resolved.getResolveScope()).findAll();
        boolean hasClosureReassigns = false;
        for (PsiReference reference : all) {
          final PsiElement ref = reference.getElement();
          if (ref instanceof GrReferenceExpression &&
              PsiUtil.isLValue(((GrReferenceExpression)ref)) &&
              ControlFlowUtils.findControlFlowOwner(ref) != flowOwner) {
            hasClosureReassigns = true;
            break;
          }
        }

        if (!hasClosureReassigns) {
          return null;
        }

        final GrExpression initializer = resolved.getInitializerGroovy();
        PsiType result = initializer != null ? initializer.getType() : null;

        final PsiManager manager = resolved.getManager();
        for (PsiReference reference : all) {
          final PsiElement ref = reference.getElement();
          if (ref instanceof GrReferenceExpression &&
              PsiUtil.isLValue(((GrReferenceExpression)ref))) {
            result = TypesUtil.getLeastUpperBoundNullable(result, TypeInferenceHelper.getInitializerFor(ref), manager);
          }
        }
        return result;
      }
    });
  }
}
