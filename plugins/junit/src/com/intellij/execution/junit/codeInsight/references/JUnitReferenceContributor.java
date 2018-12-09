// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.junit.codeInsight.references;

import com.intellij.patterns.InitialPatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JUnitReferenceContributor extends PsiReferenceContributor {
  private static PsiElementPattern.Capture<PsiLiteral> getElementPattern(String annotation, String paramName) {
    return PlatformPatterns.psiElement(PsiLiteral.class).and(new FilterPattern(new TestAnnotationFilter(annotation, paramName)));
  }

  private static PsiElementPattern.Capture<PsiLiteral> getEnumSourceNamesPattern() {
    return getElementPattern(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_ENUM_SOURCE, "names")
      .withAncestor(4, PlatformPatterns.psiElement(PsiAnnotation.class).and(new PsiJavaElementPattern<>(new InitialPatternCondition<PsiAnnotation>(PsiAnnotation.class) {
        @Override
        public boolean accepts(@Nullable Object o, ProcessingContext context) {
          if (o instanceof PsiAnnotation) {
            PsiAnnotationMemberValue mode = ((PsiAnnotation)o).findAttributeValue("mode");
            if (mode instanceof PsiReferenceExpression) {
              String referenceName = ((PsiReferenceExpression)mode).getReferenceName();
              return "INCLUDE".equals(referenceName) || "EXCLUDE".equals(referenceName);
            }
          }
          return false;
        }
      })));
  }

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(getElementPattern(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE, "value"), new PsiReferenceProvider() {
      @Override
      @NotNull
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return new MethodSourceReference[]{new MethodSourceReference((PsiLiteral)element)};
      }
    });
    registrar.registerReferenceProvider(getEnumSourceNamesPattern(), new PsiReferenceProvider() {
      @Override
      @NotNull
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return new EnumSourceReference[] {new EnumSourceReference((PsiLiteral)element)};
      }
    });
    registrar.registerReferenceProvider(getElementPattern(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_FILE_SOURCE, "resources"), new PsiReferenceProvider() {
      @Override
      @NotNull
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return FileReferenceSet.createSet(element, false, false, false).getAllReferences();
      }
    });
  }

  private static class TestAnnotationFilter implements ElementFilter {

    private final String myAnnotation;
    private final String myParameterName;

    TestAnnotationFilter(String annotation, @NotNull @NonNls String parameterName) {
      myAnnotation = annotation;
      myParameterName = parameterName;
    }

    @Override
    public boolean isAcceptable(Object element, PsiElement context) {
      PsiNameValuePair pair = PsiTreeUtil.getParentOfType(context, PsiNameValuePair.class, false, PsiMember.class, PsiStatement.class, PsiCall.class);
      if (pair == null) return false;
      String name = ObjectUtils.notNull(pair.getName(), PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
      if (!myParameterName.equals(name)) return false;
      PsiAnnotation annotation = PsiTreeUtil.getParentOfType(pair, PsiAnnotation.class);
      if (annotation == null) return false;
      return myAnnotation.equals(annotation.getQualifiedName());
    }

    @Override
    public boolean isClassAcceptable(Class hintClass) {
      return PsiLiteral.class.isAssignableFrom(hintClass);
    }
  }
}
