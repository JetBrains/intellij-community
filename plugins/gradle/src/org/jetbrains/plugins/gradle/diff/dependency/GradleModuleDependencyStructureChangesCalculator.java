package org.jetbrains.plugins.gradle.diff.dependency;

import com.intellij.openapi.externalSystem.model.project.ExternalModuleDependency;
import com.intellij.openapi.externalSystem.service.project.change.ExternalProjectChangesCalculationContext;
import com.intellij.openapi.roots.ModuleOrderEntry;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 2/20/12 11:10 AM
 */
public class GradleModuleDependencyStructureChangesCalculator
  extends AbstractGradleDependencyStructureChangesCalculator<ExternalModuleDependency, ModuleOrderEntry>
{
  
  @Override
  public void doCalculate(@NotNull ExternalModuleDependency gradleEntity,
                          @NotNull ModuleOrderEntry intellijEntity,
                          @NotNull ExternalProjectChangesCalculationContext context)
  {
    // Assuming that the modules referenced by the given dependencies are compared independently.
  }

  @NotNull
  @Override
  public Object getIdeKey(@NotNull ModuleOrderEntry entity) {
    final String intellijModuleName = entity.getModuleName();
    if (intellijModuleName == null) {
      return "";
    }
    return intellijModuleName;
  }

  @NotNull
  @Override
  public Object getGradleKey(@NotNull ExternalModuleDependency entity, @NotNull ExternalProjectChangesCalculationContext context) {
    return entity.getTarget().getName();
  }
}
