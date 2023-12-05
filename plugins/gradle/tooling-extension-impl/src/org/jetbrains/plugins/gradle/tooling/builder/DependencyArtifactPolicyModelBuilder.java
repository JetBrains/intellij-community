// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder;

import org.gradle.api.Project;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder;
import org.jetbrains.plugins.gradle.model.internal.DependencyArtifactPolicyModel;
import org.jetbrains.plugins.gradle.model.internal.ParentDependencyArtifactPolicyParameter;
import org.jetbrains.plugins.gradle.tooling.internal.DependencyArtifactPolicyModelImpl;

import static com.intellij.gradle.toolingExtension.impl.util.GradleDependencyArtifactPolicyUtil.DOWNLOAD_SOURCES_FORCE_PROPERTY_NAME;
import static com.intellij.gradle.toolingExtension.impl.util.GradleDependencyArtifactPolicyUtil.DOWNLOAD_SOURCES_PROPERTY_NAME;

public class DependencyArtifactPolicyModelBuilder implements ParameterizedToolingModelBuilder<ParentDependencyArtifactPolicyParameter> {
  @Override
  public boolean canBuild(String modelName) {
    return DependencyArtifactPolicyModel.class.getName().equals(modelName);
  }

  @Override
  public Class<ParentDependencyArtifactPolicyParameter> getParameterType() {
    return ParentDependencyArtifactPolicyParameter.class;
  }

  @Override
  public Object buildAll(String modelName, Project project) {
    return buildAll(modelName, null, project);
  }

  @Override
  public Object buildAll(String modelName, ParentDependencyArtifactPolicyParameter parameter, Project project) {
    DependencyArtifactPolicyModel policy = build(project, parameter);
    IdeaPlugin ideaPlugin = findIdeaPluginFor(project);
    if (ideaPlugin != null) {
      ideaPlugin.getModel().getModule().setDownloadSources(policy.isDownloadSources());
      ideaPlugin.getModel().getModule().setDownloadJavadoc(policy.isDownloadJavadoc());
    }
    return policy;
  }

  private DependencyArtifactPolicyModel build(Project project, ParentDependencyArtifactPolicyParameter parentPolicy) {
    boolean downloadSources = shouldDownloadSources(project, parentPolicy);
    boolean downloadJavadoc = shouldDownloadJavadocs(project, parentPolicy);
    return new DependencyArtifactPolicyModelImpl(downloadSources, downloadJavadoc);
  }

  private boolean shouldDownloadSources(Project project, ParentDependencyArtifactPolicyParameter parentPolicy) {
    // this is necessary for the explicit 'Download sources' action, and also to ensure that sources can be disabled for the headless idea
    // regardless of user settings
    String forcePropertyValue = System.getProperty(DOWNLOAD_SOURCES_FORCE_PROPERTY_NAME);
    if (forcePropertyValue != null) {
      return Boolean.parseBoolean(forcePropertyValue);
    }

    IdeaPlugin ideaPlugin = findIdeaPluginFor(project);
    if (ideaPlugin != null) {
      return ideaPlugin.getModel().getModule().isDownloadSources();
    }
    if (parentPolicy != null) {
      return parentPolicy.isDownloadSources();
    }
    // default IDE policy
    return Boolean.parseBoolean(System.getProperty(DOWNLOAD_SOURCES_PROPERTY_NAME, "false"));
  }

  private boolean shouldDownloadJavadocs(Project project, ParentDependencyArtifactPolicyParameter parentPolicy) {
    IdeaPlugin ideaPlugin = findIdeaPluginFor(project);
    if (ideaPlugin != null) {
      return ideaPlugin.getModel().getModule().isDownloadJavadoc();
    }
    if (parentPolicy != null) {
      return parentPolicy.isDownloadJavadoc();
    }
    return false;
  }

  private IdeaPlugin findIdeaPluginFor(Project project) {
    return project.getPlugins().findPlugin(IdeaPlugin.class);
  }
}
