package org.jetbrains.plugins.gradle.diff.dependency;

import com.intellij.openapi.roots.ModuleOrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.diff.GradleChangesCalculationContext;
import org.jetbrains.plugins.gradle.diff.dependency.GradleAbstractDependencyStructureChangesCalculator;
import org.jetbrains.plugins.gradle.model.gradle.GradleModuleDependency;

/**
 * @author Denis Zhdanov
 * @since 2/20/12 11:10 AM
 */
public class GradleModuleDependencyStructureChangesCalculator
  extends GradleAbstractDependencyStructureChangesCalculator<GradleModuleDependency, ModuleOrderEntry>
{
  
  @Override
  public void doCalculate(@NotNull GradleModuleDependency gradleEntity,
                          @NotNull ModuleOrderEntry intellijEntity,
                          @NotNull GradleChangesCalculationContext context)
  {
    // Assuming that the modules referenced by the given dependencies are compared independently.
  }

  @NotNull
  @Override
  public Object getIntellijKey(@NotNull ModuleOrderEntry entity) {
    final String intellijModuleName = entity.getModuleName();
    if (intellijModuleName == null) {
      return "";
    }
    return intellijModuleName;
  }

  @NotNull
  @Override
  public Object getGradleKey(@NotNull GradleModuleDependency entity, @NotNull GradleChangesCalculationContext context) {
    // TODO den consider known changes here.
    return entity.getTarget().getName();
  }
}
