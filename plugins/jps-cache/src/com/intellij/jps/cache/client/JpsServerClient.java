package com.intellij.jps.cache.client;

import com.intellij.jps.cache.model.AffectedModule;
import com.intellij.jps.cache.model.OutputLoadResult;
import com.intellij.jps.cache.ui.SegmentedProgressIndicatorManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Set;

public interface JpsServerClient {
  @NotNull
  Set<String> getAllCacheKeys(@NotNull Project project, @NotNull String branchName);
  @Nullable
  File downloadMetadataById(@NotNull String metadataId, @NotNull File targetDir);
  File downloadCacheById(@NotNull SegmentedProgressIndicatorManager downloadIndicatorManager, @NotNull String cacheId,
                         @NotNull File targetDir);
  List<OutputLoadResult> downloadCompiledModules(@NotNull SegmentedProgressIndicatorManager downloadIndicatorManager,
                                                 @NotNull List<AffectedModule> affectedModules);
  static JpsServerClient getServerClient() {
    return TemporaryCacheServerClient.INSTANCE;
  }
}