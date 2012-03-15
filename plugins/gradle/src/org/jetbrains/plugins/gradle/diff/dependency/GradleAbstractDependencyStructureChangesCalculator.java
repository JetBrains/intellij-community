package org.jetbrains.plugins.gradle.diff.dependency;

import com.intellij.openapi.roots.ExportableOrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.diff.GradleChangesCalculationContext;
import org.jetbrains.plugins.gradle.diff.GradleStructureChangesCalculator;
import org.jetbrains.plugins.gradle.model.gradle.GradleDependency;
import org.jetbrains.plugins.gradle.model.id.GradleAbstractDependencyId;
import org.jetbrains.plugins.gradle.model.id.GradleEntityIdMapper;

/**
 * Manages common dependency properties like 'scope', 'exported'.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/20/12 11:17 AM
 */
public abstract class GradleAbstractDependencyStructureChangesCalculator<G extends GradleDependency, I extends ExportableOrderEntry>
  implements GradleStructureChangesCalculator<G, I>
{
  @Override
  public void calculate(@NotNull G gradleEntity, @NotNull I intellijEntity, @NotNull GradleChangesCalculationContext context) {
    final GradleAbstractDependencyId id = GradleEntityIdMapper.mapEntityToId(gradleEntity);
    if (gradleEntity.getScope() != intellijEntity.getScope()) {
      context.register(new GradleDependencyScopeChange(id, gradleEntity.getScope(), intellijEntity.getScope()));
    }
    if (gradleEntity.isExported() != intellijEntity.isExported()) {
      context.register(new GradleDependencyExportedChange(id, gradleEntity.isExported(), intellijEntity.isExported()));
    }
    
    doCalculate(gradleEntity, intellijEntity, context);
  }
  
  protected abstract void doCalculate(@NotNull G gradleEntity, @NotNull I intellijEntity, @NotNull GradleChangesCalculationContext context);
}
