package com.intellij.jps.cache.client;

import java.io.IOException;
import java.util.Set;

public interface JpsCacheServerClient {
  Set<String> getAllCacheKeys() throws IOException;
  Set<String> getAllBinaryKeys() throws IOException;
}
