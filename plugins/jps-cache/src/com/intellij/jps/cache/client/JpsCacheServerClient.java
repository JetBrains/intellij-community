package com.intellij.jps.cache.client;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Set;
import java.util.function.Consumer;

public interface JpsCacheServerClient {
  @NotNull
  Set<String> getAllCacheKeys();
  @NotNull
  Set<String> getAllBinaryKeys();
  void downloadCacheByIdAsynchronously(@NotNull Project project, @NotNull String cacheId, @NotNull File targetDir,
                                       @NotNull Consumer<File> callbackOnSuccess);
  void downloadCompiledModuleByNameAndHash(@NotNull Project project, @NotNull String moduleName, @NotNull String moduleHash, @NotNull File targetDir);
}
