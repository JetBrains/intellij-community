package org.jetbrains.plugins.gradle.diff.dependency;

import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData;
import com.intellij.openapi.externalSystem.service.project.change.ExternalProjectChangesCalculationContext;
import com.intellij.openapi.roots.ModuleOrderEntry;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 2/20/12 11:10 AM
 */
public class GradleModuleDependencyStructureChangesCalculator
  extends AbstractGradleDependencyStructureChangesCalculator<ModuleDependencyData, ModuleOrderEntry>
{
  
  @Override
  public void doCalculate(@NotNull ModuleDependencyData gradleEntity,
                          @NotNull ModuleOrderEntry intellijEntity,
                          @NotNull ExternalProjectChangesCalculationContext context)
  {
    // Assuming that the modules referenced by the given dependencies are compared independently.
  }

  @NotNull
  public Object getIdeKey(@NotNull ModuleOrderEntry entity) {
    final String intellijModuleName = entity.getModuleName();
    if (intellijModuleName == null) {
      return "";
    }
    return intellijModuleName;
  }

  @NotNull
  public Object getGradleKey(@NotNull ModuleDependencyData entity, @NotNull ExternalProjectChangesCalculationContext context) {
    return entity.getTarget().getName();
  }
}
