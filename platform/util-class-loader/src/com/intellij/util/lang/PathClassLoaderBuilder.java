// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;

public final class PathClassLoaderBuilder {
  private static final boolean isClassPathIndexEnabledGlobalValue = Boolean.parseBoolean(System.getProperty("idea.classpath.index.enabled", "true"));

  List<Path> files = Collections.emptyList();
  Set<Path> pathsWithProtectionDomain;
  ClassLoader myParent;
  boolean lockJars;
  boolean useCache;
  boolean isClassPathIndexEnabled;
  boolean acceptUnescapedUrls;
  boolean preloadJarContents = true;
  boolean myAllowBootstrapResources;
  boolean errorOnMissingJar = true;
  boolean lazyClassloadingCaches;
  boolean logJarAccess;
  @Nullable CachePoolImpl cachePool;
  @Nullable Predicate<Path> cachingCondition;

  PathClassLoaderBuilder() { }

  public @NotNull PathClassLoaderBuilder urls(@NotNull List<URL> urls) {
    List<Path> files = new ArrayList<>(urls.size());
    for (URL url : urls) {
      files.add(Paths.get(url.getPath()));
    }
    this.files = files;
    return this;
  }

  public @NotNull PathClassLoaderBuilder files(@NotNull List<Path> paths) {
    this.files = paths;
    return this;
  }

  // Presense of this method is also checked in JUnitDevKitPatcher
  PathClassLoaderBuilder urlsFromAppClassLoader(ClassLoader classLoader) {
    if (classLoader instanceof URLClassLoader) {
      URL[] urls = ((URLClassLoader)classLoader).getURLs();
      files = new ArrayList<>(urls.length);
      for (URL url : urls) {
        files.add(Paths.get(url.getPath()));
      }
      return this;
    }

    String[] parts = System.getProperty("java.class.path").split(System.getProperty("path.separator"));
    files = new ArrayList<>(parts.length);
    for (String s : parts) {
      files.add(new File(s).toPath());
    }
    return this;
  }

  /**
   * Marks URLs that are signed by Sun/Oracle and whose signatures must be verified.
   */
  @NotNull PathClassLoaderBuilder urlsWithProtectionDomain(@NotNull Set<Path> value) {
    pathsWithProtectionDomain = value;
    return this;
  }

  public @NotNull PathClassLoaderBuilder parent(ClassLoader parent) {
    myParent = parent;
    return this;
  }

  /**
   * ZipFile handles opened in JarLoader will be kept in SoftReference. Depending on OS, the option significantly speeds up classloading
   * from libraries. Caveat: for Windows opened handle will lock the file preventing its modification.
   * Thus, the option is recommended when jars are not modified or process that uses this option is transient.
   */
  public @NotNull PathClassLoaderBuilder allowLock() {
    lockJars = true;
    return this;
  }

  public @NotNull PathClassLoaderBuilder allowLock(boolean lockJars) {
    this.lockJars = lockJars;
    return this;
  }

  /**
   * Build backward index of packages / class or resource names that allows avoiding IO during classloading.
   */
  public @NotNull PathClassLoaderBuilder useCache() {
    useCache = true;
    return this;
  }

  public @NotNull PathClassLoaderBuilder useCache(boolean useCache) {
    this.useCache = useCache;
    return this;
  }

  /**
   * FileLoader will save list of files / packages under its root and use this information instead of walking filesystem for
   * speedier classloading. Should be used only when the caches could be properly invalidated, e.g. when new file appears under
   * FileLoader's root. Currently, the flag is used for faster unit test / developed Idea running, because Idea's make (as of 14.1) ensures deletion of
   * such information upon appearing new file for output root.
   * N.b. Idea make does not ensure deletion of cached information upon deletion of some file under local root but false positives are not a
   * logical error since code is prepared for that and disk access is performed upon class / resource loading.
   * See also Builder#usePersistentClasspathIndexForLocalClassDirectories.
   */
  public @NotNull PathClassLoaderBuilder usePersistentClasspathIndexForLocalClassDirectories() {
    this.isClassPathIndexEnabled = isClassPathIndexEnabledGlobalValue;
    return this;
  }

  public @NotNull PathClassLoaderBuilder logJarAccess(boolean logJarAccess) {
    this.logJarAccess = logJarAccess;
    return this;
  }

  /**
   * Requests the class loader being built to use cache and, if possible, retrieve and store the cached data from a special cache pool
   * that can be shared between several loaders.
   *
   * @param pool      cache pool
   * @param condition a custom policy to provide a possibility to prohibit caching for some URLs.
   */
  public @NotNull PathClassLoaderBuilder useCache(@NotNull UrlClassLoader.CachePool pool, @NotNull Predicate<Path> condition) {
    useCache = true;
    cachePool = (CachePoolImpl)pool;
    cachingCondition = condition;
    return this;
  }

  public @NotNull PathClassLoaderBuilder allowUnescaped() {
    acceptUnescapedUrls = true;
    return this;
  }

  public @NotNull PathClassLoaderBuilder noPreload() {
    preloadJarContents = false;
    return this;
  }

  public @NotNull PathClassLoaderBuilder allowBootstrapResources() {
    return allowBootstrapResources(true);
  }

  public @NotNull PathClassLoaderBuilder allowBootstrapResources(boolean allowBootstrapResources) {
    myAllowBootstrapResources = allowBootstrapResources;
    return this;
  }

  public @NotNull PathClassLoaderBuilder setLogErrorOnMissingJar(boolean log) {
    errorOnMissingJar = log;
    return this;
  }

  /**
   * Package contents information in Jar/File loaders will be lazily retrieved / cached upon classloading.
   * Important: this option will result in much smaller initial overhead but for bulk classloading (like complete IDE start) it is less
   * efficient (in number of disk / native code accesses / CPU spent) than combination of useCache / usePersistentClasspathIndexForLocalClassDirectories.
   */
  public @NotNull PathClassLoaderBuilder useLazyClassloadingCaches(boolean pleaseBeLazy) {
    lazyClassloadingCaches = pleaseBeLazy;
    return this;
  }

  public @NotNull PathClassLoaderBuilder autoAssignUrlsWithProtectionDomain() {
    Set<Path> result = new HashSet<>();
    for (Path path : files) {
      if (isUrlNeedsProtectionDomain(path)) {
        result.add(path);
      }
    }
    pathsWithProtectionDomain = result;
    return this;
  }

  public @NotNull UrlClassLoader get() {
    return new UrlClassLoader(this);
  }

  private static boolean isUrlNeedsProtectionDomain(@NotNull Path file) {
    String path = file.toString();
    // BouncyCastle needs a protection domain
    if (path.endsWith(".jar")) {
      int offset = path.lastIndexOf(file.getFileSystem().getSeparator().charAt(0)) + 1;
      //noinspection SpellCheckingInspection
      if (path.startsWith("bcprov-", offset) || path.startsWith("bcpkix-", offset)) {
        return true;
      }
    }
    return false;
  }
}
