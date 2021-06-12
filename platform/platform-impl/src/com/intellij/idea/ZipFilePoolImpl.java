// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.util.lang.ClassLoadingLocks;
import com.intellij.util.lang.ImmutableZipEntry;
import com.intellij.util.lang.ImmutableZipFile;
import com.intellij.util.lang.ZipFilePool;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public final class ZipFilePoolImpl extends ZipFilePool {
  private final Map<Path, MyEntryResolver> pool = new ConcurrentHashMap<>();
  private final ClassLoadingLocks<Path> lock = new ClassLoadingLocks<>();

  @Override
  public @NotNull ImmutableZipFile loadZipFile(@NotNull Path file) throws IOException {
    MyEntryResolver resolver = pool.get(file);
    if (resolver == null) {
      // doesn't make sense to use pool for requests from class loader (requested only once per class loader)
      return ImmutableZipFile.load(file);
    }
    else {
      return resolver.zipFile;
    }
  }

  @Override
  public @NotNull ZipFilePool.EntryResolver load(@NotNull Path file) throws IOException {
    MyEntryResolver resolver = pool.get(file);
    if (resolver == null) {
      synchronized (lock.getOrCreateLock(file)) {
        resolver = pool.get(file);
        if (resolver == null) {
          ImmutableZipFile zipFile = ImmutableZipFile.load(file);
          resolver = new MyEntryResolver(zipFile);
          pool.put(file, resolver);
        }
      }
    }
    return resolver;
  }

  private static final class MyEntryResolver implements ZipFilePool.EntryResolver {
    private final ImmutableZipFile zipFile;

    MyEntryResolver(ImmutableZipFile zipFile) {
      this.zipFile = zipFile;
    }

    @Override
    public byte @Nullable [] loadZipEntry(@NotNull String path) throws IOException {
      ImmutableZipEntry entry = zipFile.getEntry(path.charAt(0) == '/' ? path.substring(1) : path);
      return entry == null ? null : entry.getData(zipFile);
    }

    @Override
    public String toString() {
      return zipFile.toString();
    }
  }

  public void clear() {
    pool.clear();
  }
}
