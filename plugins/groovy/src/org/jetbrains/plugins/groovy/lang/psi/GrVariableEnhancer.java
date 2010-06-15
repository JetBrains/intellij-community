package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;

/**
 * @author peter
 */
public abstract class GrVariableEnhancer {
  public static final ExtensionPointName<GrVariableEnhancer> EP_NAME = ExtensionPointName.create("org.intellij.groovy.variableEnhancer");

  @Nullable
  public abstract PsiType getVariableType(GrVariable variable);

  @Nullable
  public static PsiType getEnhancedType(final GrVariable variable) {
    for (GrVariableEnhancer enhancer : GrVariableEnhancer.EP_NAME.getExtensions()) {
      final PsiType type = enhancer.getVariableType(variable);
      if (type != null) {
        return type;
      }
    }

    return null;
  }

}
