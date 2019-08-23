package com.intellij.jps.cache.client;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Set;

public interface JpsCacheServerClient {
  @NotNull
  Set<String> getAllCacheKeys();
  @NotNull
  Set<String> getAllBinaryKeys();
}
