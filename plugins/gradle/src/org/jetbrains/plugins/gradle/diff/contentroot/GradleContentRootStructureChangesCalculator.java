package org.jetbrains.plugins.gradle.diff.contentroot;

import com.intellij.openapi.externalSystem.model.project.ContentRootData;
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
  implements ExternalProjectStructureChangesCalculator<ContentRootData, ModuleAwareContentRoot>
{
  @Override
  public void calculate(@NotNull ContentRootData gradleEntity,
                        @NotNull ModuleAwareContentRoot ideEntity,
                        @NotNull ExternalProjectChangesCalculationContext context)
  {
  }
  
  // TODO den implement
//  @NotNull
//  @Override
//  public Object getIdeKey(@NotNull ModuleAwareContentRoot entity) {
//    return EntityIdMapper.mapEntityToId(entity);
//  }
//
//  @NotNull
//  @Override
//  public Object getGradleKey(@NotNull ContentRootData entity, @NotNull ExternalProjectChangesCalculationContext context) {
//    // TODO den consider the known changes 
//    return EntityIdMapper.mapEntityToId(entity);
//  }
}
