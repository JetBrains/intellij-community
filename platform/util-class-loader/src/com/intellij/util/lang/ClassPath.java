// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.openapi.diagnostic.LoggerRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.jar.Attributes;

public final class ClassPath {
  private static final Loader[] EMPTY_LOADERS = new Loader[0];

  public static final String CLASSPATH_JAR_FILE_NAME_PREFIX = "classpath";

  static final boolean recordLoadingInfo = Boolean.getBoolean("idea.record.classpath.info");
  static final boolean recordLoadingTime = recordLoadingInfo || Boolean.getBoolean("idea.record.classloading.stats");

  static final boolean logLoadingInfo = Boolean.getBoolean("idea.log.classpath.info");

  private static final Collection<Map.Entry<String, Path>> loadedClasses;
  private static final AtomicLong ourTotalTime = new AtomicLong();
  private static final AtomicInteger ourTotalRequests = new AtomicInteger();
  private static final ThreadLocal<Boolean> doingTiming = new ThreadLocal<>();

  private final List<Path> files;
  private final @Nullable ResourceFileFactory resourceFileFactory;
  private final List<Loader> loaders = new ArrayList<>();

  private volatile boolean allUrlsWereProcessed;

  private final AtomicInteger lastLoaderProcessed = new AtomicInteger();
  private final Map<Path, Loader> loaderMap = new HashMap<>();
  private final ClasspathCache cache = new ClasspathCache();
  private final Set<Path> filesWithProtectionDomain;

  // true implies that the .jar file will not be modified in the lifetime of the JarLoader
  final boolean lockJars;
  private final boolean useCache;
  final boolean preloadJarContents;
  final boolean isClassPathIndexEnabled;
  private final @Nullable CachePoolImpl cachePool;
  private final @Nullable Predicate<Path> cachingCondition;
  final boolean errorOnMissingJar;

  static {
    // insertion order must be preserved
    loadedClasses = recordLoadingInfo ? new ConcurrentLinkedQueue<>() : null;

    if (logLoadingInfo) {
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println("Classloading requests: " + ClassPath.class.getClassLoader() + "," +
                           ourTotalRequests + ", time:" + (ourTotalTime.get() / 1000000) + "ms");

      }, "Shutdown hook for tracing classloading information"));
    }
  }

  ClassPath(@NotNull List<Path> files,
            @NotNull Set<Path> filesWithProtectionDomain,
            @NotNull UrlClassLoader.Builder configuration,
            @Nullable ResourceFileFactory resourceFileFactory) {
    lockJars = configuration.lockJars;
    useCache = configuration.useCache;
    preloadJarContents = configuration.preloadJarContents;
    cachePool = configuration.cachePool;
    cachingCondition = configuration.cachingCondition;
    isClassPathIndexEnabled = configuration.isClassPathIndexEnabled;
    errorOnMissingJar = configuration.errorOnMissingJar;
    this.filesWithProtectionDomain = filesWithProtectionDomain;

    this.files = new ArrayList<>(files.size());
    this.resourceFileFactory = resourceFileFactory;
    if (!files.isEmpty()) {
      for (int i = files.size() - 1; i >= 0; i--) {
        this.files.add(files.get(i));
      }
    }
  }

  public @Nullable ResourceFileFactory getResourceFileFactory() {
    return resourceFileFactory;
  }

  public interface ResourceFileFactory {
    ResourceFile create(Path file) throws IOException;
  }

  public synchronized void reset(@NotNull List<Path> paths) {
    lastLoaderProcessed.set(0);
    allUrlsWereProcessed = false;
    loaders.clear();
    loaderMap.clear();
    cache.clearCache();
    addFiles(paths);
  }

  public static @NotNull Collection<Map.Entry<String, Path>> getLoadedClasses() {
    return new ArrayList<>(loadedClasses);
  }

  // in nanoseconds
  public static long getTotalTime() {
    return ourTotalTime.get();
  }

  public static long getTotalRequests() {
    return ourTotalRequests.get();
  }

  /** @deprecated adding URLs to classpath at runtime could lead to hard-to-debug errors */
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  void addURL(Path path) {
    addFiles(Collections.singletonList(path));
  }

  private synchronized void addFiles(List<Path> files) {
    for (int i = files.size() - 1; i >= 0; i--) {
      this.files.add(files.get(i));
    }
    allUrlsWereProcessed = false;
  }

  public @Nullable Resource getResource(@NotNull String resourceName) {
    long started = startTiming();
    try {
      String shortName;
      Resource resource;
      int i;
      if (useCache) {
        shortName = ClasspathCache.transformName(resourceName);

        boolean allUrlsWereProcessed = this.allUrlsWereProcessed;
        i = allUrlsWereProcessed ? 0 : lastLoaderProcessed.get();

        resource = cache.iterateLoaders(resourceName, resourceName, shortName);
        if (resource != null) {
          return resource;
        }
        else if (allUrlsWereProcessed) {
          return null;
        }
      }
      else {
        i = 0;
        shortName = null;
      }

      Loader loader;
      while ((loader = getLoader(i++)) != null) {
        if (useCache && !loader.containsName(resourceName, shortName)) {
          continue;
        }

        resource = loader.getResource(resourceName);
        if (resource != null) {
          if (loadedClasses != null) {
            loadedClasses.add(new AbstractMap.SimpleImmutableEntry<>(resourceName, loader.path));
          }
          return resource;
        }
      }
    }
    finally {
      logInfo(started, resourceName);
    }

    return null;
  }

  public Enumeration<URL> getResources(@NotNull String name) {
    if (useCache && allUrlsWereProcessed) {
      Collection<Loader> loaderSet = new LinkedHashSet<>();
      cache.collectLoaders(name, loaderSet);

      if (name.endsWith("/")) {
        cache.collectLoaders(name.substring(0, name.length() - 1), loaderSet);
      }
      else {
        cache.collectLoaders(name + "/", loaderSet);
      }

      if (loaderSet.isEmpty()) {
        return Collections.emptyEnumeration();
      }
      return new ResourceEnumeration(name, loaderSet.toArray(EMPTY_LOADERS));
    }
    else {
      return new UncachedResourceEnumeration(name, this);
    }
  }

  private @Nullable Loader getLoader(int i) {
    // volatile read
    if (i < lastLoaderProcessed.get()) {
      return loaders.get(i);
    }

    return getLoaderSlowPath(i);
  }

  private synchronized @Nullable Loader getLoaderSlowPath(int i) {
    while (loaders.size() < i + 1) {
      Path path;
      int size = files.size();
      if (size == 0) {
        if (useCache) {
          allUrlsWereProcessed = true;
        }
        return null;
      }
      path = files.remove(size - 1);

      if (loaderMap.containsKey(path)) {
        continue;
      }

      try {
        Loader loader = createLoader(path);
        if (loader != null) {
          if (useCache) {
            initLoaderCache(path, loader);
          }
          loaders.add(loader);
          // volatile write
          loaderMap.put(path, loader);
          lastLoaderProcessed.incrementAndGet();
        }
      }
      catch (IOException e) {
        LoggerRt.getInstance(ClassPath.class).info("path: " + path, e);
      }
    }

    return loaders.get(i);
  }

  public @NotNull List<Path> getBaseUrls() {
    List<Path> result = new ArrayList<>();
    for (Loader loader : loaders) {
      result.add(loader.path);
    }
    return result;
  }

  private @Nullable Loader createLoader(@NotNull Path file) throws IOException {
    BasicFileAttributes fileAttributes;
    try {
      fileAttributes = Files.readAttributes(file, BasicFileAttributes.class);
    }
    catch (NoSuchFileException ignore) {
      return null;
    }

    if (fileAttributes.isDirectory()) {
      return new FileLoader(file, this);
    }
    else if (!fileAttributes.isRegularFile()) {
      return null;
    }

    JarLoader loader;
    if (filesWithProtectionDomain.contains(file)) {
      loader = new SecureJarLoader(file, this);
    }
    else {
      loader = new JarLoader(file, this,
                             resourceFileFactory == null ? new JdkZipResourceFile(file, lockJars, false) : resourceFileFactory.create(file));
    }

    String filePath = file.toString();
    if (filePath.startsWith(CLASSPATH_JAR_FILE_NAME_PREFIX, filePath.lastIndexOf('/') + 1)) {
      String[] referencedJars = loadManifestClasspath(loader);
      if (referencedJars != null) {
        long startReferenced = logLoadingInfo ? System.nanoTime() : 0;
        List<Path> urls = new ArrayList<>(referencedJars.length);
        for (String referencedJar : referencedJars) {
          try {
            urls.add(Paths.get(UrlClassLoader.urlToFilePath(referencedJar)));
          }
          catch (Exception e) {
            LoggerRt.getInstance(ClassPath.class).warn("file: " + file + " / " + referencedJar, e);
          }
        }
        addFiles(urls);
        if (logLoadingInfo) {
          //noinspection UseOfSystemOutOrSystemErr
          System.out.println("Loaded all " + referencedJars.length + " files " + (System.nanoTime() - startReferenced) / 1000000 + "ms");
        }
      }
    }
    return loader;
  }

  private void initLoaderCache(@NotNull Path file, @NotNull Loader loader) throws IOException {
    ClasspathCache.IndexRegistrar data = cachePool == null ? null : cachePool.loaderIndexCache.get(file);
    if (data == null) {
      data = loader.buildData();
      if (cachePool != null && cachingCondition != null && cachingCondition.test(file)) {
        ClasspathCache.LoaderData loaderData =
          data instanceof ClasspathCache.LoaderData ? (ClasspathCache.LoaderData)data : ((ClasspathCache.LoaderDataBuilder)data).build();
        cachePool.loaderIndexCache.put(file, loaderData);
        data = loaderData;
      }
    }
    cache.applyLoaderData(data, loader);

    if (files.isEmpty()) {
      allUrlsWereProcessed = true;
    }
  }

  Attributes getManifestData(@NotNull Path file) {
    return useCache && cachePool != null ? cachePool.getManifestData(file) : null;
  }

  void cacheManifestData(@NotNull Path file, @NotNull Attributes manifestAttributes) {
    if (useCache && cachePool != null && cachingCondition != null && cachingCondition.test(file)) {
      cachePool.cacheManifestData(file, manifestAttributes);
    }
  }

  private static final class ResourceEnumeration implements Enumeration<URL> {
    private int index;
    private Resource resource;
    private final String name;
    private final String shortName;
    private final Loader[] loaders;

    ResourceEnumeration(@NotNull String name, Loader[] loaders) {
      this.name = name;
      shortName = ClasspathCache.transformName(name);
      this.loaders = loaders;
    }

    private boolean next() {
      if (resource != null) {
        return true;
      }

      long started = startTiming();
      try {
        Loader loader;
        while (index < loaders.length) {
          loader = loaders[index++];
          if (!loader.containsName(name, shortName)) {
            resource = null;
            continue;
          }
          resource = loader.getResource(name);
          if (resource != null) {
            return true;
          }
        }
      }
      finally {
        logInfo(started, name);
      }

      return false;
    }

    @Override
    public boolean hasMoreElements() {
      return next();
    }

    @Override
    public URL nextElement() {
      if (!next()) {
        throw new NoSuchElementException();
      }

      Resource resource = this.resource;
      this.resource = null;
      return resource.getURL();
    }
  }

  private static final class UncachedResourceEnumeration implements Enumeration<URL> {
    private int index;
    private Resource resource;
    private final String name;
    private final String shortName;
    private final ClassPath classPath;

    UncachedResourceEnumeration(@NotNull String name, @NotNull ClassPath classPath) {
      this.name = name;
      shortName = ClasspathCache.transformName(name);
      this.classPath = classPath;
    }

    private boolean next() {
      if (resource != null) {
        return true;
      }

      long started = startTiming();
      try {
        Loader loader;
        while ((loader = classPath.getLoader(index++)) != null) {
          if (classPath.useCache && !loader.containsName(name, shortName)) {
            continue;
          }
          resource = loader.getResource(name);
          if (resource != null) {
            return true;
          }
        }
      }
      finally {
        logInfo(started, name);
      }

      return false;
    }

    @Override
    public boolean hasMoreElements() {
      return next();
    }

    @Override
    public URL nextElement() {
      if (!next()) {
        throw new NoSuchElementException();
      }

      Resource resource = this.resource;
      this.resource = null;
      return resource.getURL();
    }
  }

  static @Nullable Resource findInLoader(@NotNull Loader loader, @NotNull String name) {
    Resource resource = loader.getResource(name);
    if (resource != null && loadedClasses != null) {
      loadedClasses.add(new AbstractMap.SimpleImmutableEntry<>(name, loader.path));
    }
    return resource;
  }

  private static long startTiming() {
    if (!recordLoadingTime || doingTiming.get() != null) {
      return 0;
    }
    else {
      doingTiming.set(Boolean.TRUE);
      return System.nanoTime();
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void logInfo(long started, String resourceName) {
    if (started == 0 || !recordLoadingTime) {
      return;
    }

    doingTiming.set(null);

    long time = System.nanoTime() - started;
    long totalTime = ourTotalTime.addAndGet(time);
    int totalRequests = ourTotalRequests.incrementAndGet();
    if (logLoadingInfo) {
      if (time > 3000000L) {
        System.out.println(time / 1000000 + " ms for " + resourceName);
      }
      if (totalRequests % 10000 == 0) {
        System.out.println(ClassPath.class.getClassLoader() + ", requests:" + ourTotalRequests + ", time:" + (totalTime / 1000000) + "ms");
      }
    }
  }

  private static String @Nullable [] loadManifestClasspath(@NotNull JarLoader loader) {
    try {
      String classPath = loader.getClassPathManifestAttribute();
      if (classPath != null) {
        String[] urls = classPath.split(" ");
        if (urls.length > 0 && urls[0].startsWith("file:")) {
          return urls;
        }
      }
    }
    catch (Exception ignore) {
    }
    return null;
  }
}