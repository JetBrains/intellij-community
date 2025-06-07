// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;

public abstract class GrVariableEnhancer {
  public static final ExtensionPointName<GrVariableEnhancer> EP_NAME = ExtensionPointName.create("org.intellij.groovy.variableEnhancer");

  public abstract @Nullable PsiType getVariableType(GrVariable variable);

  public static @Nullable PsiType getEnhancedType(final GrVariable variable) {
    for (GrVariableEnhancer enhancer : EP_NAME.getExtensions()) {
      final PsiType type = enhancer.getVariableType(variable);
      if (type != null) {
        return type;
      }
    }

    return null;
  }

}
