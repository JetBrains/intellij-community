package org.jetbrains.plugins.gradle.config;

import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 3/13/12 3:53 PM
 */
public abstract class GradleConfigNotifierAdapter implements GradleConfigNotifier {

  @Override
  public void onGradleHomeChange(@Nullable String oldPath, @Nullable String newPath) {
  }

  @Override
  public void onLinkedProjectPathChange(@Nullable String oldPath, @Nullable String newPath) {
  }

  @Override
  public void onPreferLocalGradleDistributionToWrapperChange(boolean preferLocalToWrapper) {
  }

  @Override
  public void onBulkChangeStart() {
  }

  @Override
  public void onBulkChangeEnd() {
  }
}
