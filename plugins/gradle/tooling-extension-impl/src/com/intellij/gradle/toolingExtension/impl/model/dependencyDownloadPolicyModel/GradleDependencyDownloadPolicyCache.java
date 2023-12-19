// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel;

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import com.intellij.gradle.toolingExtension.impl.util.GradleProjectUtil;
import com.intellij.gradle.toolingExtension.util.GradleNegotiationUtil;
import org.gradle.api.Project;
import org.gradle.tooling.model.ProjectIdentifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApiStatus.Internal
public class GradleDependencyDownloadPolicyCache {

  private final @NotNull ModelBuilderContext context;
  private final @NotNull ConcurrentMap<ProjectIdentifier, GradleDependencyDownloadPolicy> policies;

  private GradleDependencyDownloadPolicyCache(@NotNull ModelBuilderContext context) {
    this.context = context;
    this.policies = new ConcurrentHashMap<>();
  }

  public @NotNull GradleDependencyDownloadPolicy getDependencyDownloadPolicy(@NotNull Project project) {
    ProjectIdentifier projectIdentifier = GradleProjectUtil.getProjectIdentifier(project);
    GradleDependencyDownloadPolicy policy = policies.get(projectIdentifier);
    if (policy == null) {
      String projectDisplayName = GradleNegotiationUtil.getProjectDisplayName(project);
      context.getMessageReporter().createMessage()
        .withGroup(Messages.DEPENDENCY_DOWNLOAD_POLICY_MODEL_CACHE_GET_GROUP)
        .withTitle("Gradle dependency download policy aren't found")
        .withText("Gradle dependency download policy for " + projectDisplayName + " wasn't collected.")
        .withStackTrace()
        .withKind(Message.Kind.INTERNAL)
        .reportMessage(project);
      return new DefaultGradleDependencyDownloadPolicy();
    }
    return policy;
  }

  public void setDependencyDownloadPolicy(@NotNull Project project, @NotNull GradleDependencyDownloadPolicy policy) {
    ProjectIdentifier projectIdentifier = GradleProjectUtil.getProjectIdentifier(project);
    GradleDependencyDownloadPolicy oldPolicy = policies.put(projectIdentifier, policy);
    if (oldPolicy != null) {
      String projectDisplayName = GradleNegotiationUtil.getProjectDisplayName(project);
      context.getMessageReporter().createMessage()
        .withGroup(Messages.DEPENDENCY_DOWNLOAD_POLICY_MODEL_CACHE_SET_GROUP)
        .withTitle("Gradle dependency download policy redefinition")
        .withText("Gradle dependency download policy for " + projectDisplayName + " was already collected.")
        .withStackTrace()
        .withKind(Message.Kind.INTERNAL)
        .reportMessage(project);
    }
  }

  /**
   * Marks that a project dependency artifact policy is loaded with errors.
   * This mark means that error for {@code project} is already processed and reported.
   */
  public void markDependencyDownloadPolicyAsError(@NotNull Project project) {
    ProjectIdentifier projectIdentifier = GradleProjectUtil.getProjectIdentifier(project);
    policies.put(projectIdentifier, new DefaultGradleDependencyDownloadPolicy());
  }

  private static final @NotNull ModelBuilderContext.DataProvider<GradleDependencyDownloadPolicyCache> INSTANCE_PROVIDER =
    GradleDependencyDownloadPolicyCache::new;

  public static @NotNull GradleDependencyDownloadPolicyCache getInstance(@NotNull ModelBuilderContext context) {
    return context.getData(INSTANCE_PROVIDER);
  }
}
