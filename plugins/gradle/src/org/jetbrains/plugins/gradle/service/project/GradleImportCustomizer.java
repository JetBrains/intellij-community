// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to customize {@link GradleProjectResolver} processing.
 * <p/>
 * Only a single extension is expected per platform.
 *
 * @author Vladislav.Soroka
 */

public abstract class GradleImportCustomizer {

  private static final ExtensionPointName<GradleImportCustomizer> EP_NAME =
    ExtensionPointName.create("org.jetbrains.plugins.gradle.importCustomizer");

  public abstract String getPlatformPrefix();

  public static @Nullable GradleImportCustomizer get() {
    GradleImportCustomizer result = null;
    if (!PlatformUtils.isIntelliJ()) {
      final String platformPrefix = PlatformUtils.getPlatformPrefix();
      for (GradleImportCustomizer provider : EP_NAME.getExtensions()) {
        if (StringUtil.equals(platformPrefix, provider.getPlatformPrefix())) {
          assert result == null : "Multiple gradle import customizer extensions found";
          result = provider;
        }
      }
    }
    return result;
  }

  public abstract boolean useExtraJvmArgs();
}
