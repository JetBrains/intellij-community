// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.openapi.diagnostic.LoggerRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
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
  private static final ResourceStringLoaderIterator ourResourceIterator = new ResourceStringLoaderIterator();
  private static final LoaderCollector ourLoaderCollector = new LoaderCollector();
  public static final String CLASSPATH_JAR_FILE_NAME_PREFIX = "classpath";

  static final boolean recordLoadingInfo = Boolean.getBoolean("idea.record.classpath.info");
  static final boolean logLoadingInfo = Boolean.getBoolean("idea.log.classpath.info");

  private static final Collection<Map.Entry<String, Path>> loadedClasses;
  private static final AtomicLong ourTotalTime = new AtomicLong();
  private static final AtomicInteger ourTotalRequests = new AtomicInteger();
  private static final ThreadLocal<Boolean> doingTiming = new ThreadLocal<>();

  private final List<Path> files;
  private final List<Loader> loaders = new ArrayList<>();

  private volatile boolean allUrlsWereProcessed;

  private final AtomicInteger lastLoaderProcessed = new AtomicInteger();
  private final Map<Path, Loader> loadersMap = new HashMap<>();
  private final ClasspathCache cache = new ClasspathCache();
  private final Set<Path> filesWithProtectionDomain;

  final boolean lockJars; // true implies that the .jar file will not be modified in the lifetime of the JarLoader
  private final boolean useCache;
  final boolean preloadJarContents;
  final boolean isClassPathIndexEnabled;
  final boolean lazyClassloadingCaches;
  private final @Nullable CachePoolImpl cachePool;
  private final @Nullable Predicate<Path> cachingCondition;
  final boolean errorOnMissingJar;

  static {
    // insertion order must be preserved
    loadedClasses = (recordLoadingInfo || Boolean.getBoolean("idea.record.classloading.stats")) ? new ConcurrentLinkedQueue<>() : null;

    if (logLoadingInfo) {
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println("Classloading requests: " + ClassPath.class.getClassLoader() + "," +
                           ourTotalRequests + ", time:" + (ourTotalTime.get() / 1000000) + "ms");

      }, "Shutdown hook for tracing classloading information"));
    }
  }

  ClassPath(List<Path> files, @NotNull Set<Path> filesWithProtectionDomain, @NotNull PathClassLoaderBuilder configuration) {
    lazyClassloadingCaches = configuration.lazyClassloadingCaches;
    lockJars = configuration.lockJars;
    useCache = configuration.useCache && !lazyClassloadingCaches;
    preloadJarContents = configuration.preloadJarContents;
    cachePool = configuration.cachePool;
    cachingCondition = configuration.cachingCondition;
    isClassPathIndexEnabled = configuration.isClassPathIndexEnabled;
    errorOnMissingJar = configuration.errorOnMissingJar;
    this.filesWithProtectionDomain = filesWithProtectionDomain;

    this.files = new ArrayList<>(files.size());
    if (!files.isEmpty()) {
      for (int i = files.size() - 1; i >= 0; i--) {
        this.files.add(files.get(i));
      }
    }
  }

  public synchronized void reset(@NotNull List<Path> paths) {
    lastLoaderProcessed.set(0);
    allUrlsWereProcessed = false;
    loaders.clear();
    loadersMap.clear();
    cache.clearCache();
    push(paths);
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
    push(Collections.singletonList(path));
  }

  private void push(List<Path> paths) {
    if (!paths.isEmpty()) {
      synchronized (this.files) {
        for (int i = paths.size() - 1; i >= 0; i--) {
          this.files.add(paths.get(i));
        }
        allUrlsWereProcessed = false;
      }
    }
  }

  public @Nullable Resource getResource(@NotNull String resourceName) {
    long started = startTiming();
    try {
      String shortName = ClasspathCache.transformName(resourceName);
      Resource resource;
      int i;
      if (useCache) {
        boolean allUrlsWereProcessed = this.allUrlsWereProcessed;
        i = allUrlsWereProcessed ? 0 : lastLoaderProcessed.get();

        resource = cache.iterateLoaders(resourceName, ourResourceIterator, resourceName, this, shortName);
        if (resource != null) {
          return resource;
        }
        else if (allUrlsWereProcessed) {
          return null;
        }
      }
      else {
        i = 0;
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
      logInfo(this, started, resourceName);
    }

    return null;
  }

  public Enumeration<URL> getResources(@NotNull String name) {
    return new ResourceEnumeration(name);
  }

  private @Nullable Loader getLoader(int i) {
    if (i < lastLoaderProcessed.get()) { // volatile read
      return loaders.get(i);
    }

    return getLoaderSlowPath(i);
  }

  private synchronized @Nullable Loader getLoaderSlowPath(int i) {
    while (loaders.size() < i + 1) {
      Path path;
      synchronized (files) {
        int size = files.size();
        if (size == 0) {
          if (useCache) {
            allUrlsWereProcessed = true;
          }
          return null;
        }
        path = files.remove(size - 1);
      }

      if (loadersMap.containsKey(path)) {
        continue;
      }

      try {
        initLoaders(path);
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

  private void initLoaders(@NotNull Path file) throws IOException {
    String filePath = file.toString();
    Loader loader = createLoader(file, filePath.startsWith(CLASSPATH_JAR_FILE_NAME_PREFIX, filePath.lastIndexOf('/') + 1));
    if (loader != null) {
      initLoader(file, loader);
    }
  }

  private @Nullable Loader createLoader(@NotNull Path file, boolean processRecursively) throws IOException {
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

    boolean isSigned = filesWithProtectionDomain.contains(file);
    JarLoader loader = isSigned ? new SecureJarLoader(file, this) : new JarLoader(file, this);
    if (processRecursively) {
      String[] referencedJars = loadManifestClasspath(loader);
      if (referencedJars != null) {
        long s2 = recordLoadingInfo ? System.nanoTime() : 0;
        List<Path> urls = new ArrayList<>(referencedJars.length);
        for (String referencedJar : referencedJars) {
          try {
            urls.add(Paths.get(new URI(referencedJar)));
          }
          catch (Exception e) {
            LoggerRt.getInstance(ClassPath.class).warn("file: " + file + " / " + referencedJar, e);
          }
        }
        push(urls);
        if (logLoadingInfo) {
          //noinspection UseOfSystemOutOrSystemErr
          System.out.println("Loaded all " + referencedJars.length + " files " + (System.nanoTime() - s2) / 1000000 + "ms");
        }
      }
    }
    return loader;
  }

  private void initLoader(@NotNull Path path, @NotNull Loader loader) throws IOException {
    if (useCache) {
      ClasspathCache.LoaderData data = cachePool == null ? null : cachePool.getCachedData(path);
      if (data == null) {
        data = loader.buildData();
        if (cachePool != null && cachingCondition != null && cachingCondition.test(path)) {
          cachePool.cacheData(path, data);
        }
      }
      cache.applyLoaderData(data, loader);

      boolean lastOne;
      synchronized (files) {
        lastOne = files.isEmpty();
      }

      if (lastOne) {
        allUrlsWereProcessed = true;
      }
    }
    loaders.add(loader);
    loadersMap.put(path, loader);
    lastLoaderProcessed.incrementAndGet(); // volatile write
  }

  Attributes getManifestData(@NotNull Path file) {
    return useCache && cachePool != null ? cachePool.getManifestData(file) : null;
  }

  void cacheManifestData(@NotNull Path file, @NotNull Attributes manifestAttributes) {
    if (useCache && cachePool != null && cachingCondition != null && cachingCondition.test(file)) {
      cachePool.cacheManifestData(file, manifestAttributes);
    }
  }

  private final class ResourceEnumeration implements Enumeration<URL> {
    private int myIndex;
    private Resource myResource;
    private final @NotNull String myName;
    private final String myShortName;
    private final List<Loader> myLoaders;

    ResourceEnumeration(@NotNull String name) {
      myName = name;
      myShortName = ClasspathCache.transformName(name);
      List<Loader> loaders = null;

      if (useCache && allUrlsWereProcessed) {
        Collection<Loader> loadersSet = new LinkedHashSet<>();
        cache.iterateLoaders(name, ourLoaderCollector, loadersSet, this, myShortName);

        if (name.endsWith("/")) {
          cache.iterateLoaders(name.substring(0, name.length() - 1), ourLoaderCollector, loadersSet, this, myShortName);
        }
        else {
          cache.iterateLoaders(name + "/", ourLoaderCollector, loadersSet, this, myShortName);
        }

        loaders = new ArrayList<>(loadersSet);
      }

      myLoaders = loaders;
    }

    private boolean next() {
      if (myResource != null) {
        return true;
      }

      long started = startTiming();
      try {
        Loader loader;
        if (myLoaders != null) {
          while (myIndex < myLoaders.size()) {
            loader = myLoaders.get(myIndex++);
            if (!loader.containsName(myName, myShortName)) {
              myResource = null;
              continue;
            }
            myResource = loader.getResource(myName);
            if (myResource != null) {
              return true;
            }
          }
        }
        else {
          while ((loader = getLoader(myIndex++)) != null) {
            if (useCache && !loader.containsName(myName, myShortName)) {
              continue;
            }
            myResource = loader.getResource(myName);
            if (myResource != null) {
              return true;
            }
          }
        }
      }
      finally {
        logInfo(ClassPath.this, started, myName);
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
      else {
        Resource resource = myResource;
        myResource = null;
        return resource.getURL();
      }
    }
  }

  private static final class ResourceStringLoaderIterator implements ClasspathCache.LoaderIterator<Resource, String, ClassPath> {
    @Override
    public Resource process(@NotNull Loader loader, @NotNull String s, @NotNull ClassPath classPath, @NotNull String shortName) {
      return loader.containsName(s, shortName) ? findInLoader(loader, s) : null;
    }

    private static @Nullable Resource findInLoader(@NotNull Loader loader, @NotNull String resourceName) {
      Resource resource = loader.getResource(resourceName);
      if (resource != null && loadedClasses != null) {
        loadedClasses.add(new AbstractMap.SimpleImmutableEntry<>(resourceName, loader.path));
      }
      return resource;
    }
  }

  private static class LoaderCollector implements ClasspathCache.LoaderIterator<Object, Collection<Loader>, Object> {
    @Override
    public Object process(@NotNull Loader loader,
                          @NotNull Collection<Loader> parameter,
                          @NotNull Object parameter2,
                          @NotNull String shortName) {
      parameter.add(loader);
      return null;
    }
  }

  private static long startTiming() {
    if (loadedClasses == null || doingTiming.get() != null) {
      return 0;
    }

    doingTiming.set(Boolean.TRUE);
    return System.nanoTime();
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void logInfo(ClassPath path, long started, String resourceName) {
    if (started == 0 || loadedClasses == null) {
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
        System.out.println(path.getClass().getClassLoader() + ", requests:" + ourTotalRequests + ", time:" + (totalTime / 1000000) + "ms");
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