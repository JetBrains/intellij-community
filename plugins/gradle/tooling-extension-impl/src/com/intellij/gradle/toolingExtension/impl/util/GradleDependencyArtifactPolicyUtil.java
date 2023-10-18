// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util;

import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.jetbrains.annotations.Nullable;

public final class GradleDependencyArtifactPolicyUtil {

  private static final String DOWNLOAD_SOURCES_FORCE_PROPERTY_NAME = "idea.gradle.download.sources.force";
  private static final String DOWNLOAD_SOURCES_PROPERTY_NAME = "idea.gradle.download.sources";

  public static boolean shouldDownloadSources(@Nullable final IdeaPlugin ideaPlugin) {
    // this is necessary for the explicit 'Download sources' action, and also to ensure that sources can be disabled for the headless idea
    // regardless of user settings
    String forcePropertyValue = System.getProperty(DOWNLOAD_SOURCES_FORCE_PROPERTY_NAME);
    if (forcePropertyValue != null) {
      return Boolean.parseBoolean(forcePropertyValue);
    }
    // we should respect project level settings declared in the 'build.gradle' file
    if (ideaPlugin != null) {
      return ideaPlugin.getModel().getModule().isDownloadSources();
    }
    // default IDE policy
    return Boolean.parseBoolean(System.getProperty(DOWNLOAD_SOURCES_PROPERTY_NAME, "false"));
  }

  public static boolean shouldDownloadJavadoc(@Nullable final IdeaPlugin ideaPlugin) {
    if (ideaPlugin != null) {
      final IdeaModule ideaModule = ideaPlugin.getModel().getModule();
      return ideaModule.isDownloadJavadoc();
    }
    return false;
  }
}
