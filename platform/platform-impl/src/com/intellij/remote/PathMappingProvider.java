package com.intellij.remote;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.PathMappingSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * @author traff
 */
public abstract class PathMappingProvider {
  public static ExtensionPointName<PathMappingProvider> EP_NAME = ExtensionPointName.create("com.intellij.remote.pathMappingProvider");

  public static List<PathMappingProvider> getSuitableMappingProviders(final RemoteSdkAdditionalData data) {
    return Lists
      .newArrayList(Iterables.filter(Arrays.asList(EP_NAME.getExtensions()), new Predicate<PathMappingProvider>() {
        @Override
        public boolean apply(PathMappingProvider provider) {
          return provider.accepts(data);
        }
      }));
  }

  @NotNull
  public abstract String getProviderPresentableName(@NotNull RemoteSdkAdditionalData data);

  public abstract boolean accepts(@Nullable RemoteSdkAdditionalData data);

  @NotNull
  public abstract PathMappingSettings getPathMappingSettings(@NotNull Project project, @NotNull RemoteSdkAdditionalData data);
}
