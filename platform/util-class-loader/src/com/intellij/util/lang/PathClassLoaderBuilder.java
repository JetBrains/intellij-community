// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.util.PathUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

public final class PathClassLoaderBuilder {
  private static final boolean isClassPathIndexEnabledGlobalValue = Boolean.parseBoolean(System.getProperty("idea.classpath.index.enabled", "true"));

  List<URL> urls = Collections.emptyList();
  List<Path> paths = Collections.emptyList();
  Set<URL> urlsWithProtectionDomain;
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
  @Nullable Predicate<URL> cachingCondition;

  boolean myUrlsInterned;

  PathClassLoaderBuilder() { }

  public @NotNull PathClassLoaderBuilder urls(@NotNull List<URL> urls) {
    this.urls = urls;
    return this;
  }

  public @NotNull PathClassLoaderBuilder paths(@NotNull List<Path> paths) {
    this.paths = paths;
    return this;
  }

  public @NotNull PathClassLoaderBuilder urls(URL @NotNull ... urls) {
    this.urls = Arrays.asList(urls);
    return this;
  }

  // Presense of this method is also checked in JUnitDevKitPatcher
  PathClassLoaderBuilder urlsFromAppClassLoader(ClassLoader classLoader) {
    if (classLoader instanceof URLClassLoader) {
      return urls(((URLClassLoader)classLoader).getURLs());
    }
    String[] parts = System.getProperty("java.class.path").split(System.getProperty("path.separator"));
    urls = new ArrayList<>(parts.length);
    for (String s : parts) {
      try {
        urls.add(new File(s).toURI().toURL());
      }
      catch (IOException ignored) {
      }
    }
    return this;
  }

  /**
   * Marks URLs that are signed by Sun/Oracle and whose signatures must be verified.
   */
  @NotNull PathClassLoaderBuilder urlsWithProtectionDomain(@NotNull Set<URL> urls) {
    urlsWithProtectionDomain = urls;
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

  public @NotNull PathClassLoaderBuilder urlsInterned() {
    myUrlsInterned = true;
    return this;
  }

  /**
   * Requests the class loader being built to use cache and, if possible, retrieve and store the cached data from a special cache pool
   * that can be shared between several loaders.
   *
   * @param pool      cache pool
   * @param condition a custom policy to provide a possibility to prohibit caching for some URLs.
   */
  public @NotNull PathClassLoaderBuilder useCache(@NotNull UrlClassLoader.CachePool pool, @NotNull Predicate<URL> condition) {
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
    Set<URL> result = new HashSet<>();
    for (URL url : urls) {
      if (isUrlNeedsProtectionDomain(url)) {
        result.add(url);
      }
    }
    return urlsWithProtectionDomain(result);
  }

  public @NotNull UrlClassLoader get() {
    return new UrlClassLoader(this);
  }

  private static boolean isUrlNeedsProtectionDomain(@NotNull URL url) {
    String name = PathUtilRt.getFileName(url.getPath());
    //noinspection SpellCheckingInspection
    return name.endsWith(".jar") && (name.startsWith("bcprov-") || name.startsWith("bcpkix-"));  // BouncyCastle needs protection domain
  }
}
