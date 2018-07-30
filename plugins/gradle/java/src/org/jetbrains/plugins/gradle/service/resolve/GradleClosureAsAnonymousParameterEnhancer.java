/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.service.resolve;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWildcardType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureAsAnonymousParameterEnhancer;

/**
 * @author Vladislav.Soroka
 * @since 9/6/13
 */
public class GradleClosureAsAnonymousParameterEnhancer extends ClosureAsAnonymousParameterEnhancer {
  @Nullable
  @Override
  protected PsiType getClosureParameterType(GrClosableBlock closure, int index) {

    PsiFile file = closure.getContainingFile();
    if (file == null || !FileUtilRt.extensionEquals(file.getName(), GradleConstants.EXTENSION)) return null;

    PsiType psiType = super.getClosureParameterType(closure, index);
    if (psiType instanceof PsiWildcardType) {
      PsiWildcardType wildcardType = (PsiWildcardType)psiType;
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