package org.jetbrains.android.compiler;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public interface AndroidLightBuildProvider {
  ExtensionPointName<AndroidLightBuildProvider> EP_NAME = ExtensionPointName.create("org.jetbrains.android.lightBuildProvider");

  boolean toPerformLightBuild(@NotNull RunConfiguration runConfiguration);
}
