package org.jetbrains.plugins.gradle.diff.dependency;

import com.intellij.openapi.roots.ExportableOrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.diff.GradleChangesCalculationContext;
import org.jetbrains.plugins.gradle.diff.GradleStructureChangesCalculator;
import org.jetbrains.plugins.gradle.model.gradle.GradleDependency;
import org.jetbrains.plugins.gradle.model.id.AbstractGradleDependencyId;
import org.jetbrains.plugins.gradle.model.id.GradleEntityIdMapper;

/**
 * Manages common dependency properties like 'scope', 'exported'.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/20/12 11:17 AM
 */
public abstract class AbstractGradleDependencyStructureChangesCalculator<G extends GradleDependency, I extends ExportableOrderEntry>
  implements GradleStructureChangesCalculator<G, I>
{
  @Override
  public void calculate(@NotNull G gradleEntity, @NotNull I ideEntity, @NotNull GradleChangesCalculationContext context) {
    final AbstractGradleDependencyId id = GradleEntityIdMapper.mapEntityToId(gradleEntity);
    if (gradleEntity.getScope() != ideEntity.getScope()) {
      context.register(new GradleDependencyScopeChange(id, gradleEntity.getScope(), ideEntity.getScope()));
    }
    if (gradleEntity.isExported() != ideEntity.isExported()) {
      context.register(new GradleDependencyExportedChange(id, gradleEntity.isExported(), ideEntity.isExported()));
    }
    
    doCalculate(gradleEntity, ideEntity, context);
  }
  
  protected abstract void doCalculate(@NotNull G gradleEntity, @NotNull I intellijEntity, @NotNull GradleChangesCalculationContext context);
}
