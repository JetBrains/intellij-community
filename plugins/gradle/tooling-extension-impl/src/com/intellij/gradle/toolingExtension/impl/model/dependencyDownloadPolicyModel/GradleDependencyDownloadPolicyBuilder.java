// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel;

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import com.intellij.gradle.toolingExtension.impl.util.GradleIdeaPluginUtil;
import org.gradle.api.Project;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;


@ApiStatus.Internal
public class GradleDependencyDownloadPolicyBuilder extends AbstractModelBuilderService {

  private static final String DOWNLOAD_SOURCES_FORCE_PROPERTY_NAME = "idea.gradle.download.sources.force";
  private static final String DOWNLOAD_SOURCES_PROPERTY_NAME = "idea.gradle.download.sources";

  @Override
  public boolean canBuild(String modelName) {
    return GradleDependencyDownloadPolicy.class.getName().equals(modelName);
  }

  @Override
  public Object buildAll(@NotNull String modelName, @NotNull Project project, @NotNull ModelBuilderContext context) {
    DefaultGradleDependencyDownloadPolicy dependencyDownloadPolicy = new DefaultGradleDependencyDownloadPolicy();
    dependencyDownloadPolicy.setDownloadSources(shouldDownloadSources(context, project));
    dependencyDownloadPolicy.setDownloadJavadoc(shouldDownloadJavadocs(context, project));

    setIdeaPluginDependencyDownloadPolicy(project, dependencyDownloadPolicy);

    GradleDependencyDownloadPolicyCache.getInstance(context)
      .setDependencyDownloadPolicy(project, dependencyDownloadPolicy);

    return dependencyDownloadPolicy;
  }

  private static void setIdeaPluginDependencyDownloadPolicy(@NotNull Project project, @NotNull GradleDependencyDownloadPolicy policy) {
    project.getPlugins().withType(IdeaPlugin.class, plugin -> {
      IdeaModule module = plugin.getModel().getModule();
      module.setDownloadSources(policy.isDownloadSources());
      module.setDownloadJavadoc(policy.isDownloadJavadoc());
    });
  }

  private static @Nullable GradleDependencyDownloadPolicy getParentDependencyDownloadPolicy(
    @NotNull ModelBuilderContext context,
    @NotNull Project project
  ) {
    Project parentProject = project.getParent();
    if (parentProject == null) {
      return null;
    }
    return GradleDependencyDownloadPolicyCache.getInstance(context)
      .getDependencyDownloadPolicy(parentProject);
  }

  private static boolean shouldDownloadSources(@NotNull ModelBuilderContext context, @NotNull Project project) {
    // this is necessary for the explicit 'Download sources' action, and also to ensure that sources can be disabled for the headless idea
    // regardless of user settings
    String forcePropertyValue = System.getProperty(DOWNLOAD_SOURCES_FORCE_PROPERTY_NAME);
    if (forcePropertyValue != null) {
      return Boolean.parseBoolean(forcePropertyValue);
    }
    IdeaModule ideaModule = GradleIdeaPluginUtil.getIdeaModule(project);
    if (ideaModule != null) {
      return ideaModule.isDownloadSources();
    }
    GradleDependencyDownloadPolicy parentPolicy = getParentDependencyDownloadPolicy(context, project);
    if (parentPolicy != null) {
      return parentPolicy.isDownloadSources();
    }
    // default IDE policy
    return Boolean.parseBoolean(System.getProperty(DOWNLOAD_SOURCES_PROPERTY_NAME, "false"));
  }

  private static boolean shouldDownloadJavadocs(@NotNull ModelBuilderContext context, @NotNull Project project) {
    IdeaModule ideaModule = GradleIdeaPluginUtil.getIdeaModule(project);
    if (ideaModule != null) {
      return ideaModule.isDownloadJavadoc();
    }
    GradleDependencyDownloadPolicy parentPolicy = getParentDependencyDownloadPolicy(context, project);
    if (parentPolicy != null) {
      return parentPolicy.isDownloadJavadoc();
    }
    return false;
  }

  @Override
  public void reportErrorMessage(
    @NotNull String modelName,
    @NotNull Project project,
    @NotNull ModelBuilderContext context,
    @NotNull Exception exception
  ) {
    GradleDependencyDownloadPolicyCache.getInstance(context)
      .markDependencyDownloadPolicyAsError(project);

    context.getMessageReporter().createMessage()
      .withGroup(Messages.DEPENDENCY_DOWNLOAD_POLICY_MODEL_GROUP)
      .withKind(Message.Kind.ERROR)
      .withTitle("Dependency download policy building failure")
      .withException(exception)
      .reportMessage(project);
  }
}
