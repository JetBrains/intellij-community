package com.intellij.jps.cache.client;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface JpsServerClient {
  @NotNull
  Set<String> getAllCacheKeys();
  @NotNull
  Set<String> getAllBinaryKeys();
  void downloadCacheByIdAsynchronously(@NotNull Project project, @NotNull String cacheId, @NotNull File targetDir,
                                       @NotNull Consumer<File> callbackOnSuccess);
  void downloadCompiledModuleByNameAndHash(@NotNull Project project, @NotNull String moduleName, @NotNull String prefix, @NotNull String moduleHash,
                                           @NotNull File targetDir, @NotNull BiConsumer<File, String> callbackOnSuccess);
  void uploadBinaryData(@NotNull File uploadData, @NotNull String moduleName, @NotNull String prefix);
}