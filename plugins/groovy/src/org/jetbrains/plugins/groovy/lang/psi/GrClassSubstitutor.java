package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class GrClassSubstitutor {
  public static final ExtensionPointName<GrClassSubstitutor> EP_NAME = ExtensionPointName.create("org.intellij.groovy.classSubstitutor");
  private static final Key<CachedValue<PsiClass>> SUBSTITUTED_CLASS_KEY = Key.create("GroovySubstitutedType");

  @Nullable
  public abstract GrClassSubstitution substituteClass(@NotNull PsiClass base);

  @NotNull
  public static PsiClass getSubstitutedClass(@NotNull final PsiClass base) {
    if (!Extensions.getRootArea().getExtensionPoint(EP_NAME).hasAnyExtensions()) {
      return base;
    }

    if (base instanceof GrClassSubstitution) {
      return base;
    }

    return CachedValuesManager.getManager(base.getProject())
      .getCachedValue(base, SUBSTITUTED_CLASS_KEY, new CachedValueProvider<PsiClass>() {
        public Result<PsiClass> compute() {
          for (GrClassSubstitutor enhancer : GrClassSubstitutor.EP_NAME.getExtensions()) {
            final PsiClass type = enhancer.substituteClass(base);
            if (type != null) {
              return Result.create(type, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
            }
          }
          return Result.create(base, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
        }
      }, false);
  }

}
