package org.jetbrains.plugins.gradle.diff.dependency;

import com.intellij.openapi.externalSystem.model.project.DependencyData;
import com.intellij.openapi.externalSystem.model.project.change.DependencyExportedChange;
import com.intellij.openapi.externalSystem.model.project.change.DependencyScopeChange;
import com.intellij.openapi.externalSystem.model.project.change.ExternalProjectStructureChangesCalculator;
import com.intellij.openapi.externalSystem.model.project.id.AbstractExternalDependencyId;
import com.intellij.openapi.externalSystem.model.project.id.EntityIdMapper;
import com.intellij.openapi.externalSystem.service.project.change.ExternalProjectChangesCalculationContext;
import com.intellij.openapi.roots.ExportableOrderEntry;
import org.jetbrains.annotations.NotNull;

/**
 * Manages common dependency properties like 'scope', 'exported'.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/20/12 11:17 AM
 */
public abstract class AbstractGradleDependencyStructureChangesCalculator<G extends DependencyData, I extends ExportableOrderEntry>
  implements ExternalProjectStructureChangesCalculator<G, I>
{
  @Override
  public void calculate(@NotNull G gradleEntity, @NotNull I ideEntity, @NotNull ExternalProjectChangesCalculationContext context) {
    final AbstractExternalDependencyId id = EntityIdMapper.mapEntityToId(gradleEntity);
    if (gradleEntity.getScope() != ideEntity.getScope()) {
      context.register(new DependencyScopeChange(id, gradleEntity.getScope(), ideEntity.getScope()));
    }
    if (gradleEntity.isExported() != ideEntity.isExported()) {
      context.register(new DependencyExportedChange(id, gradleEntity.isExported(), ideEntity.isExported()));
    }
    
    doCalculate(gradleEntity, ideEntity, context);
  }
  
  protected abstract void doCalculate(@NotNull G gradleEntity, @NotNull I intellijEntity, @NotNull ExternalProjectChangesCalculationContext context);
}
