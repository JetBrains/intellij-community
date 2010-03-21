package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;

/**
 * @author peter
 */
public abstract class GrVariableEnhancer {
  public static final ExtensionPointName<GrVariableEnhancer> EP_NAME = ExtensionPointName.create("org.intellij.groovy.variableEnhancer");
  private static final Key<CachedValue<PsiType>> ENHANCED_TYPE_KEY = Key.create("GroovyEnhancedType");

  @Nullable
  public abstract PsiType getVariableType(GrVariable variable);

  public static PsiType getEnhancedType(final GrVariable variable) {
    return CachedValuesManager.getManager(variable.getProject()).getCachedValue(variable, ENHANCED_TYPE_KEY, new CachedValueProvider<PsiType>() {
          public Result<PsiType> compute() {
            for (GrVariableEnhancer enhancer : GrVariableEnhancer.EP_NAME.getExtensions()) {
              final PsiType type = enhancer.getVariableType(variable);
              if (type != null) {
                return Result.create(type, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
              }
            }

            return Result.create(null, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
          }
        }, false);
  }

}
