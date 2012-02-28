package org.jetbrains.plugins.gradle.diff;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.GradleContentRoot;
import org.jetbrains.plugins.gradle.model.id.GradleEntityIdMapper;
import org.jetbrains.plugins.gradle.model.intellij.ModuleAwareContentRoot;

/**
 * @author Denis Zhdanov
 * @since 2/27/12 7:00 PM
 */
public class GradleContentRootStructureChangesCalculator
  implements GradleStructureChangesCalculator<GradleContentRoot, ModuleAwareContentRoot>
{
  @Override
  public void calculate(@NotNull GradleContentRoot gradleEntity,
                        @NotNull ModuleAwareContentRoot intellijEntity,
                        @NotNull GradleChangesCalculationContext context)
  {
    // TODO den implement content root conflict changes here.
  }

  @NotNull
  @Override
  public Object getIntellijKey(@NotNull ModuleAwareContentRoot entity) {
    return GradleEntityIdMapper.mapEntityToId(entity);
  }

  @NotNull
  @Override
  public Object getGradleKey(@NotNull GradleContentRoot entity, @NotNull GradleChangesCalculationContext context) {
    // TODO den consider the known changes 
    return GradleEntityIdMapper.mapEntityToId(entity);
  }
}
