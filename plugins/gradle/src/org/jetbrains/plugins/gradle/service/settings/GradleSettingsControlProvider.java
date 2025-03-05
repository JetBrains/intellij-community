// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.settings;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

/**
 * Extension of Gradle Settings Control.
 *<br/><br/>
 * Implement this to provide custom Gradle settings UI components instead of default ones.
 * Only one extension should be available for current platform prefix, see {@link PlatformUtils#getPlatformPrefix}
 */
public abstract class GradleSettingsControlProvider {

  private static final ExtensionPointName<GradleSettingsControlProvider> EP_NAME =
    ExtensionPointName.create("org.jetbrains.plugins.gradle.settingsControlProvider");

  public abstract String getPlatformPrefix();

  public abstract GradleSystemSettingsControlBuilder getSystemSettingsControlBuilder(@NotNull GradleSettings initialSettings);

  public abstract GradleProjectSettingsControlBuilder getProjectSettingsControlBuilder(@NotNull GradleProjectSettings initialSettings);

  public static @NotNull GradleSettingsControlProvider get() {
    GradleSettingsControlProvider result = null;
    final String platformPrefix = PlatformUtils.getPlatformPrefix();
    for (GradleSettingsControlProvider provider : EP_NAME.getExtensions()) {
      if (StringUtil.equals(platformPrefix, provider.getPlatformPrefix())) {
        assert result == null : "Multiple GradleSettingsControlProvider extensions found";
        result = provider;
      }
    }
    return ObjectUtils.notNull(result, new DefaultProvider());
  }

  private static class DefaultProvider extends GradleSettingsControlProvider {
    @Override
    public String getPlatformPrefix() {
      return null;
    }

    @Override
    public GradleSystemSettingsControlBuilder getSystemSettingsControlBuilder(@NotNull GradleSettings initialSettings) {
      return new IdeaGradleSystemSettingsControlBuilder(initialSettings)
        // always use external storage for project files
        .dropStoreExternallyCheckBox()
        .dropDefaultProjectSettings();
    }

    @Override
    public GradleProjectSettingsControlBuilder getProjectSettingsControlBuilder(@NotNull GradleProjectSettings initialSettings) {
      return new IdeaGradleProjectSettingsControlBuilder(initialSettings)
        // hide java-specific option
        .dropResolveModulePerSourceSetCheckBox()
        .dropDelegateBuildCombobox()
        .dropTestRunnerCombobox()
        // hide this confusing option
        .dropCustomizableWrapperButton()
        // Hide bundled distribution option for a while
        .dropUseBundledDistributionButton();
    }
  }
}
