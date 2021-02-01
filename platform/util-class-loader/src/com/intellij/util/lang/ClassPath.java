// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.openapi.diagnostic.LoggerRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

@ApiStatus.Internal
public final class ClassPath {
  static final String CLASS_EXTENSION = ".class";

  public static final String CLASSPATH_JAR_FILE_NAME_PREFIX = "classpath";

  static final boolean recordLoadingInfo = Boolean.getBoolean("idea.record.classpath.info");
  static final boolean recordLoadingTime = recordLoadingInfo || Boolean.getBoolean("idea.record.classloading.stats");

  static final boolean logLoadingInfo = Boolean.getBoolean("idea.log.classpath.info");

  private static final Collection<Map.Entry<String, Path>> loadedClasses;

  private static final Measurer classLoading = new Measurer();
  private static final Measurer resourceLoading = new Measurer();
  private static final AtomicLong classDefineTotalTime = new AtomicLong();

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
  private final @Nullable Predicate<? super Path> cachingCondition;
  final boolean errorOnMissingJar;

  private final @NotNull ClassPath.ClassDataConsumer classDataConsumer;

  static {
    // insertion order must be preserved
    loadedClasses = recordLoadingInfo ? new ConcurrentLinkedQueue<>() : null;

    if (logLoadingInfo) {
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println("Classloading requests: " + ClassPath.class.getClassLoader() +
                           ", class=" + classLoading + ", resource=" + resourceLoading);

      }, "Shutdown hook for tracing classloading information"));
    }
  }

  interface ClassDataConsumer {
    boolean isByteBufferSupported(String name, @Nullable ProtectionDomain protectionDomain);

    Class<?> consumeClassData(String name, byte[] data, Loader loader, @Nullable ProtectionDomain protectionDomain) throws IOException;

    Class<?> consumeClassData(String name, ByteBuffer data, Loader loader, @Nullable ProtectionDomain protectionDomain) throws IOException;
  }

  public @Nullable ResourceFileFactory getResourceFileFactory() {
    return resourceFileFactory;
  }

  ClassPath(@NotNull List<Path> files,
            @NotNull Set<Path> filesWithProtectionDomain,
            @NotNull UrlClassLoader.Builder configuration,
            @Nullable ResourceFileFactory resourceFileFactory,
            @NotNull ClassPath.ClassDataConsumer classDataConsumer) {
    lockJars = configuration.lockJars;
    useCache = configuration.useCache;
    preloadJarContents = configuration.preloadJarContents;
    cachePool = configuration.cachePool;
    cachingCondition = configuration.cachingCondition;
    isClassPathIndexEnabled = configuration.isClassPathIndexEnabled;
    errorOnMissingJar = configuration.errorOnMissingJar;
    this.filesWithProtectionDomain = filesWithProtectionDomain;

    this.classDataConsumer = recordLoadingTime ? new MeasuringClassDataConsumer(classDataConsumer) : classDataConsumer;

    this.files = new ArrayList<>(files.size());
    this.resourceFileFactory = resourceFileFactory;
    if (!files.isEmpty()) {
      for (int i = files.size() - 1; i >= 0; i--) {
        this.files.add(files.get(i));
      }
    }
  }

  public interface ResourceFileFactory {
    ResourceFile create(Path file) throws IOException;
  }

  public synchronized void reset(@NotNull List<? extends Path> paths) {
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
  public static @NotNull Map<String, Long> getLoadingStats() {
    Map<String, Long> result = new HashMap<>(5);
    result.put("classLoadingTime", classLoading.timeCounter.get());
    result.put("classDefineTime", classDefineTotalTime.get());
    result.put("classRequests", (long)classLoading.requestCounter.get());

    result.put("resourceLoadingTime", resourceLoading.timeCounter.get());
    result.put("resourceRequests", (long)resourceLoading.requestCounter.get());

    result.put("identity", (long)ClassPath.class.hashCode());
    return result;
  }

  /** Adding URLs to classpath at runtime could lead to hard-to-debug errors */
  @ApiStatus.Internal
  synchronized void addFiles(@NotNull List<? extends Path> files) {
    for (int i = files.size() - 1; i >= 0; i--) {
      this.files.add(files.get(i));
    }
    allUrlsWereProcessed = false;
  }

  // think twice before use
  public synchronized void appendFiles(@NotNull List<? extends Path> newList) {
    Set<Path> existing = new HashSet<>(files);
    for (int i = newList.size() - 1; i >= 0; i--) {
      Path file = newList.get(i);
      if (!existing.contains(file)) {
        files.add(file);
      }
    }
    allUrlsWereProcessed = false;
  }

  public @Nullable Class<?> findClass(@NotNull String className) throws IOException {
    long start = classLoading.startTiming();
    try {
      String fileName = className.replace('.', '/') + CLASS_EXTENSION;
      int i;
      if (useCache) {
        Loader[] loaders = cache.getClassLoadersByName(fileName);
        if (loaders != null) {
          for (Loader loader : loaders) {
            if (loader.containsName(fileName)) {
              Class<?> result = findClassInLoader(fileName, className, classDataConsumer, loader);
              if (result != null) {
                return result;
              }
            }
          }
        }

        if (allUrlsWereProcessed) {
          return null;
        }

        i = lastLoaderProcessed.get();
      }
      else {
        i = 0;
      }

      Loader loader;
      while ((loader = getLoader(i++)) != null) {
        if (useCache && !loader.containsName(fileName)) {
          continue;
        }

        Class<?> result = findClassInLoader(fileName, className, classDataConsumer, loader);
        if (result != null) {
          return result;
        }
      }
    }
    finally {
      classLoading.record(start, className);
    }

    return null;
  }

  private static @Nullable Class<?> findClassInLoader(@NotNull String fileName,
                                                      @NotNull String className,
                                                      @NotNull ClassDataConsumer classConsumer,
                                                      @NotNull Loader loader) throws IOException {
    Class<?> result = loader.findClass(fileName, className, classConsumer);
    if (result == null) {
      return null;
    }
    if (loadedClasses != null) {
      loadedClasses.add(new AbstractMap.SimpleImmutableEntry<>(fileName, loader.path));
    }
    return result;
  }

  public @Nullable Resource findResource(@NotNull String resourceName) {
    long start = resourceLoading.startTiming();
    try {
      int i;
      if (useCache) {
        Loader[] loaders = cache.getLoadersByName(resourceName);
        if (loaders != null) {
          for (Loader loader : loaders) {
            if (loader.containsName(resourceName)) {
              Resource resource = loader.getResource(resourceName);
              if (resource != null) {
                if (loadedClasses != null) {
                  loadedClasses.add(new AbstractMap.SimpleImmutableEntry<>(resourceName, loader.path));
                }
                return resource;
              }
            }
          }
        }

        if (allUrlsWereProcessed) {
          return null;
        }

        i = lastLoaderProcessed.get();
      }
      else {
        i = 0;
      }

      Loader loader;
      while ((loader = getLoader(i++)) != null) {
        if (useCache && !loader.containsName(resourceName)) {
          continue;
        }

        Resource resource = loader.getResource(resourceName);
        if (resource != null) {
          if (loadedClasses != null) {
            loadedClasses.add(new AbstractMap.SimpleImmutableEntry<>(resourceName, loader.path));
          }
          return resource;
        }
      }
    }
    finally {
      resourceLoading.record(start, resourceName);
    }

    return null;
  }

  public @NotNull Enumeration<URL> getResources(@NotNull String name) {
    if (name.endsWith("/")) {
      name = name.substring(0, name.length() - 1);
    }
    if (useCache && allUrlsWereProcessed) {
      Loader[] loaders = cache.getLoadersByName(name);
      return loaders == null || loaders.length == 0 ? Collections.emptyEnumeration() : new ResourceEnumeration(name, loaders);
    }
    else {
      return new UncachedResourceEnumeration(name, this);
    }
  }

  void processResources(@NotNull String dir,
                        @NotNull Predicate<? super String> fileNameFilter,
                        @NotNull BiConsumer<? super String, ? super InputStream> consumer) throws IOException {
    if (useCache && allUrlsWereProcessed) {
      // getLoadersByName compute package name by name, so, add ending slash
      Loader[] loaders = cache.getLoadersByName(dir + '/');
      if (loaders != null) {
        for (Loader loader : loaders) {
          loader.processResources(dir, fileNameFilter, consumer);
        }
      }
    }
    else {
      int index = 0;
      Loader loader;
      while ((loader = getLoader(index++)) != null) {
        loader.processResources(dir, fileNameFilter, consumer);
      }
    }
  }

  private @Nullable Loader getLoader(int i) {
    // volatile read
    return i < lastLoaderProcessed.get() ? loaders.get(i) : getLoaderSlowPath(i);
  }

  private synchronized @Nullable Loader getLoaderSlowPath(int i) {
    while (loaders.size() < i + 1) {
      int size = files.size();
      if (size == 0) {
        if (useCache) {
          allUrlsWereProcessed = true;
        }
        return null;
      }

      Path path = files.remove(size - 1);
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
      return new FileLoader(file, isClassPathIndexEnabled);
    }
    else if (!fileAttributes.isRegularFile()) {
      return null;
    }

    JarLoader loader;
    if (filesWithProtectionDomain.contains(file)) {
      loader = new SecureJarLoader(file, this);
    }
    else {
      ResourceFile zipFile;
      if (resourceFileFactory == null) {
        zipFile = new JdkZipResourceFile(file, lockJars, preloadJarContents, false);
      }
      else {
        zipFile = resourceFileFactory.create(file);
      }
      loader = new JarLoader(file, this, zipFile);
    }

    String filePath = file.toString();
    if (filePath.startsWith(CLASSPATH_JAR_FILE_NAME_PREFIX, filePath.lastIndexOf(File.separatorChar) + 1)) {
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

  Map<Loader.Attribute, String> getManifestData(@NotNull Path file) {
    return useCache && cachePool != null ? cachePool.getManifestData(file) : null;
  }

  void cacheManifestData(@NotNull Path file, @NotNull Map<Loader.Attribute, String> manifestAttributes) {
    if (useCache && cachePool != null && cachingCondition != null && cachingCondition.test(file)) {
      cachePool.cacheManifestData(file, manifestAttributes);
    }
  }

  private static final class ResourceEnumeration implements Enumeration<URL> {
    private int index;
    private Resource resource;
    private final String name;
    private final Loader[] loaders;

    ResourceEnumeration(@NotNull String name, Loader[] loaders) {
      this.name = name;
      this.loaders = loaders;
    }

    private boolean next() {
      if (resource != null) {
        return true;
      }

      long start = resourceLoading.startTiming();
      try {
        Loader loader;
        while (index < loaders.length) {
          loader = loaders[index++];
          if (!loader.containsName(name)) {
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
        resourceLoading.record(start, name);
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
    private final ClassPath classPath;

    UncachedResourceEnumeration(@NotNull String name, @NotNull ClassPath classPath) {
      this.name = name;
      this.classPath = classPath;
    }

    private boolean next() {
      if (resource != null) {
        return true;
      }

      long start = resourceLoading.startTiming();
      try {
        Loader loader;
        while ((loader = classPath.getLoader(index++)) != null) {
          if (classPath.useCache && !loader.containsName(name)) {
            continue;
          }
          resource = loader.getResource(name);
          if (resource != null) {
            return true;
          }
        }
      }
      finally {
        resourceLoading.record(start, name);
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

  private static final class MeasuringClassDataConsumer implements ClassDataConsumer {
    private static final ThreadLocal<Boolean> doingClassDefineTiming = new ThreadLocal<>();

    private final ClassDataConsumer classDataConsumer;

    MeasuringClassDataConsumer(ClassDataConsumer classDataConsumer) {
      this.classDataConsumer = classDataConsumer;
    }

    @Override
    public boolean isByteBufferSupported(String name, @Nullable ProtectionDomain protectionDomain) {
      return classDataConsumer.isByteBufferSupported(name, protectionDomain);
    }

    @Override
    public Class<?> consumeClassData(String name,
                                     byte[] data,
                                     Loader loader,
                                     @Nullable ProtectionDomain protectionDomain) throws IOException {
      long start = startTiming();
      try {
        return classDataConsumer.consumeClassData(name, data, loader, protectionDomain);
      }
      finally {
        record(start);
      }
    }

    @Override
    public Class<?> consumeClassData(String name,
                                     ByteBuffer data,
                                     Loader loader,
                                     @Nullable ProtectionDomain protectionDomain) throws IOException {
      long start = startTiming();
      try {
        return classDataConsumer.consumeClassData(name, data, loader, protectionDomain);
      }
      finally {
        record(start);
      }
    }

    private static long startTiming() {
      if (doingClassDefineTiming.get() != null) {
        return -1;
      }
      else {
        doingClassDefineTiming.set(Boolean.TRUE);
        return System.nanoTime();
      }
    }

    private static void record(long start) {
      if (start != -1) {
        doingClassDefineTiming.set(null);
        classDefineTotalTime.addAndGet(System.nanoTime() - start);
      }
    }
  }

  private static final class Measurer {
    private final AtomicLong timeCounter = new AtomicLong();
    private final AtomicInteger requestCounter = new AtomicInteger();

    private final ThreadLocal<Boolean> doingTiming = new ThreadLocal<>();

    long startTiming() {
      if (!recordLoadingTime || doingTiming.get() != null) {
        return -1;
      }
      else {
        doingTiming.set(Boolean.TRUE);
        return System.nanoTime();
      }
    }

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    void record(long start, String resourceName) {
      if (start == -1) {
        return;
      }

      doingTiming.set(null);

      long time = System.nanoTime() - start;
      long totalTime = timeCounter.addAndGet(time);
      int totalRequests = requestCounter.incrementAndGet();
      if (logLoadingInfo) {
        if (time > 3000000L) {
          System.out.println(TimeUnit.NANOSECONDS.toMillis(time) + " ms for " + resourceName);
        }
        if (totalRequests % 10000 == 0) {
          System.out.println(ClassPath.class.getClassLoader() + ", requests: " + totalRequests +
                             ", time:" + TimeUnit.NANOSECONDS.toMillis(totalTime) + "ms");
        }
      }
    }

    @Override
    public String toString() {
      return "Measurer(time=" + TimeUnit.NANOSECONDS.toMillis(timeCounter.get()) + "ms, requests=" + requestCounter + ')';
    }
  }
}