package org.jetbrains.plugins.gradle.diff;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.GradleEntity;

/**
 * Manages common dependency properties like 'scope', 'exported'.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/20/12 11:17 AM
 */
public abstract class GradleAbstractDependencyStructureChangesCalculator<G extends GradleEntity, I>
  implements GradleStructureChangesCalculator<G, I>
{
  @Override
  public void calculate(@NotNull G gradleEntity, @NotNull I intellijEntity, @NotNull GradleChangesCalculationContext context) {
    doCalculate(gradleEntity, intellijEntity, context);
  }
  
  protected abstract void doCalculate(@NotNull G gradleEntity, @NotNull I intellijEntity, @NotNull GradleChangesCalculationContext context);
}
