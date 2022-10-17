// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.GroovyQuickFixFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GrRangeExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
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
          return GroovyBundle.message("fix.class", psiClass.getName());
        }

        @Nls
        @NotNull
        @Override
        public String getFamilyName() {
          return GroovyBundle.message("intention.family.name.fix.range.class");
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
    return switch (args.length) {
      case 1 -> GroovyBundle.message("type.doesnt.implement.comparable", args);
      case 2 -> GroovyBundle.message("type.doesnt.contain.method", args);
      default -> throw new IncorrectOperationException("incorrect args:" + Arrays.toString(args));
    };
  }

  private static class MyVisitor extends BaseInspectionVisitor {
    @NlsSafe private static final String CALL_NEXT = "next()";
    @NlsSafe private static final String CALL_PREVIOUS = "previous()";

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
        registerError(range, iterationType.getPresentableText(), CALL_NEXT);
      }
      if (previouses.length == 0) {
        registerError(range, iterationType.getPresentableText(), CALL_PREVIOUS);
      }

      if (!InheritanceUtil.isInheritor(iterationType, CommonClassNames.JAVA_LANG_COMPARABLE)) {
        registerError(range, iterationType.getPresentableText());
      }
    }
  }
}
