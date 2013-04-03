package org.jetbrains.plugins.gradle.diff.contentroot;

import com.intellij.openapi.externalSystem.model.project.ExternalContentRoot;
import com.intellij.openapi.externalSystem.model.project.change.ExternalProjectStructureChangesCalculator;
import com.intellij.openapi.externalSystem.model.project.id.EntityIdMapper;
import com.intellij.openapi.externalSystem.service.project.change.ExternalProjectChangesCalculationContext;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.externalSystem.service.project.ModuleAwareContentRoot;

/**
 * @author Denis Zhdanov
 * @since 2/27/12 7:00 PM
 */
public class GradleContentRootStructureChangesCalculator
  implements ExternalProjectStructureChangesCalculator<ExternalContentRoot, ModuleAwareContentRoot>
{
  @Override
  public void calculate(@NotNull ExternalContentRoot gradleEntity,
                        @NotNull ModuleAwareContentRoot ideEntity,
                        @NotNull ExternalProjectChangesCalculationContext context)
  {
  }

  @NotNull
  @Override
  public Object getIdeKey(@NotNull ModuleAwareContentRoot entity) {
    return EntityIdMapper.mapEntityToId(entity);
  }

  @NotNull
  @Override
  public Object getGradleKey(@NotNull ExternalContentRoot entity, @NotNull ExternalProjectChangesCalculationContext context) {
    // TODO den consider the known changes 
    return EntityIdMapper.mapEntityToId(entity);
  }
}
