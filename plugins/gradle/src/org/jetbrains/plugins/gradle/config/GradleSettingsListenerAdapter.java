package org.jetbrains.plugins.gradle.config;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener;

/**
 * @author Denis Zhdanov
 * @since 3/13/12 3:53 PM
 */
public abstract class GradleSettingsListenerAdapter implements GradleSettingsListener {

  @Override
  public void onGradleHomeChange(@Nullable String oldPath, @Nullable String newPath) {
  }

  @Override
  public void onLinkedProjectPathChange(@Nullable String oldPath, @Nullable String newPath) {
  }

  @Override
  public void onPreferLocalGradleDistributionToWrapperChange(boolean currentValue) {
  }

  @Override
  public void onServiceDirectoryPathChange(@Nullable String oldPath, @Nullable String newPath) {
  }

  @Override
  public void onUseAutoImportChange(boolean currentValue) {
  }

  @Override
  public void onBulkChangeStart() {
  }

  @Override
  public void onBulkChangeEnd() {
  }
}
