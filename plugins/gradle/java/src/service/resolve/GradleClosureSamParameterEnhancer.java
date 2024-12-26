// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWildcardType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureSamParameterEnhancer;

/**
 * @author Vladislav.Soroka
 */
public final class GradleClosureSamParameterEnhancer extends ClosureSamParameterEnhancer {
  @Override
  protected @Nullable PsiType getClosureParameterType(@NotNull GrFunctionalExpression expression, int index) {

    PsiFile file = expression.getContainingFile();
    if (file == null || !FileUtilRt.extensionEquals(file.getName(), GradleConstants.EXTENSION)) return null;

    PsiType psiType = super.getClosureParameterType(expression, index);
    if (psiType instanceof PsiWildcardType wildcardType) {
      if (wildcardType.isSuper() && wildcardType.getBound() != null &&
          wildcardType.getBound().equalsToText(GradleCommonClassNames.GRADLE_API_SOURCE_SET)) {
        return wildcardType.getBound();
      }
      if (wildcardType.isSuper() && wildcardType.getBound() != null &&
          wildcardType.getBound().equalsToText(GradleCommonClassNames.GRADLE_API_DISTRIBUTION)) {
        return wildcardType.getBound();
      }
    }

    return null;
  }
}