/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.*;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.arithmetic.GrRangeExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrRangeType;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GroovyRangeTypeCheckInspection extends BaseInspection {

  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new MyVisitor();
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return GroovyInspectionBundle.message("incorrect.range.argument");
  }

  @Override
  protected GroovyFix buildFix(@NotNull PsiElement location) {
    final GrRangeExpression range = (GrRangeExpression)location;
    final PsiType type = range.getType();
    final List<GroovyFix> fixes = new ArrayList<>(3);
    if (type instanceof GrRangeType) {
      PsiType iterationType = ((GrRangeType)type).getIterationType();
      if (!(iterationType instanceof PsiClassType)) return null;
      final PsiClass psiClass = ((PsiClassType)iterationType).resolve();
      if (!(psiClass instanceof GrTypeDefinition)) return null;

      final GroovyResolveResult[] nexts = ResolveUtil.getMethodCandidates(iterationType, "next", range);
      final GroovyResolveResult[] previouses = ResolveUtil.getMethodCandidates(iterationType, "previous", range);
      final GroovyResolveResult[] compareTos = ResolveUtil.getMethodCandidates(iterationType, "compareTo", range, iterationType);


      if (countImplementations(psiClass, nexts)==0) {
        fixes.add(GroovyQuickFixFactory.getInstance().createAddMethodFix("next", (GrTypeDefinition)psiClass));
      }
      if (countImplementations(psiClass, previouses) == 0) {
        fixes.add(GroovyQuickFixFactory.getInstance().createAddMethodFix("previous", (GrTypeDefinition)psiClass));
      }

      if (!InheritanceUtil.isInheritor(iterationType, CommonClassNames.JAVA_LANG_COMPARABLE) ||
          countImplementations(psiClass, compareTos) == 0) {
        fixes.add(GroovyQuickFixFactory.getInstance().createAddClassToExtendsFix((GrTypeDefinition)psiClass, CommonClassNames.JAVA_LANG_COMPARABLE));
      }

      return new GroovyFix() {
        @Override
        protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
          for (GroovyFix fix : fixes) {
            fix.applyFix(project, descriptor);
          }
        }

        @NotNull
        @Override
        public String getName() {
          return GroovyInspectionBundle.message("fix.class", psiClass.getName());
        }

        @Nls
        @NotNull
        @Override
        public String getFamilyName() {
          return "Fix range class";
        }
      };
    }
    return null;
  }

  private static int countImplementations(PsiClass clazz, GroovyResolveResult[] methods) {
    if (clazz.isInterface()) return methods.length;
    int result = 0;
    for (GroovyResolveResult method : methods) {
      final PsiElement el = method.getElement();
      if (el instanceof PsiMethod && !((PsiMethod)el).hasModifierProperty(PsiModifier.ABSTRACT)) result++;
      else if (el instanceof PsiField) result++;
    }
    return result;
  }

  @Override
  protected String buildErrorString(Object... args) {
    switch (args.length) {
      case 1:
        return GroovyInspectionBundle.message("type.doesnt.implemnt.comparable", args);
      case 2:
        return GroovyInspectionBundle.message("type.doesnt.contain.method", args);
      default:
        throw new IncorrectOperationException("incorrect args:" + Arrays.toString(args));
    }
  }

  private static class MyVisitor extends BaseInspectionVisitor {
    @Override
    public void visitRangeExpression(@NotNull GrRangeExpression range) {
      super.visitRangeExpression(range);
      final PsiType type = range.getType();
      if (!(type instanceof GrRangeType)) return;
      final PsiType iterationType = ((GrRangeType)type).getIterationType();
      if (iterationType == null) return;

      final GroovyResolveResult[] nexts = ResolveUtil.getMethodCandidates(iterationType, "next", range, PsiType.EMPTY_ARRAY);
      final GroovyResolveResult[] previouses = ResolveUtil.getMethodCandidates(iterationType, "previous", range, PsiType.EMPTY_ARRAY);
      if (nexts.length == 0) {
        registerError(range, iterationType.getPresentableText(), "next()");
      }
      if (previouses.length == 0) {
        registerError(range, iterationType.getPresentableText(), "previous()");
      }

      if (!InheritanceUtil.isInheritor(iterationType, CommonClassNames.JAVA_LANG_COMPARABLE)) {
        registerError(range, iterationType.getPresentableText());
      }
    }
  }
}
