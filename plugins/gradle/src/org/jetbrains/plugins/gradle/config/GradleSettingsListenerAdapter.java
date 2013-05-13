package org.jetbrains.plugins.gradle.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener;

import java.util.Collection;

/**
 * @author Denis Zhdanov
 * @since 3/13/12 3:53 PM
 */
public abstract class GradleSettingsListenerAdapter implements GradleSettingsListener {

  @Override
  public void onGradleHomeChange(@Nullable String oldPath, @Nullable String newPath, @NotNull String linkedProjectPath) {
  }

  @Override
  public void onPreferLocalGradleDistributionToWrapperChange(boolean currentValue, @NotNull String linkedProjectPath) {
  }

  @Override
  public void onServiceDirectoryPathChange(@Nullable String oldPath, @Nullable String newPath) {
  }

  @Override
  public void onProjectsLinked(@NotNull Collection<GradleProjectSettings> settings) {
  }

  @Override
  public void onProjectsUnlinked(@NotNull Collection<String> linkedProjectPaths) {
  }

  @Override
  public void onUseAutoImportChange(boolean currentValue, @NotNull String linkedProjectPath) {
  }

  @Override
  public void onBulkChangeStart() {
  }

  @Override
  public void onBulkChangeEnd() {
  }
}
