package com.intellij.jps.cache.client;

import com.intellij.jps.cache.model.AffectedModule;
import com.intellij.jps.cache.ui.SegmentedProgressIndicatorManager;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface JpsServerClient {
  @NotNull
  Set<String> getAllCacheKeys();
  @NotNull
  Set<String> getAllBinaryKeys();
  @Nullable
  File downloadMetadataById(@NotNull String metadataId, @NotNull File targetDir);
  Pair<Boolean, File> downloadCacheById(@NotNull SegmentedProgressIndicatorManager indicatorManager,
                                        @NotNull String cacheId, @NotNull File targetDir);
  Pair<Boolean, Map<File, String>> downloadCompiledModules(@NotNull SegmentedProgressIndicatorManager indicatorManager,
                                                           @NotNull List<AffectedModule> affectedModules);
  void uploadBinaryData(@NotNull File uploadData, @NotNull String moduleName, @NotNull String prefix);
}