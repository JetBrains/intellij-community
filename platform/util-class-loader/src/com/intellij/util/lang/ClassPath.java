// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.Attributes;

@ApiStatus.Internal
public final class ClassPath {
  public static final String CLASSPATH_JAR_FILE_NAME_PREFIX = "classpath";

  // record loaded class name and source path
  static final boolean recordLoadingInfo = Boolean.getBoolean("idea.record.classpath.info");
  // record class and resource loading time
  static final boolean recordLoadingTime = recordLoadingInfo || Boolean.getBoolean("idea.record.classloading.stats");
  static final boolean logLoadingInfo = Boolean.getBoolean("idea.log.classpath.info");

  // DCEVM support
  private static final boolean isNewClassLoadingEnabled = false;

  private static final Collection<Map.Entry<String, Path>> loadedClasses;

  private static final Measurer classLoading = new Measurer();
  private static final Measurer resourceLoading = new Measurer();
  private static final AtomicLong classDefineTotalTime = new AtomicLong();

  private Path[] files;
  private boolean filesConvertedToDefaultFs;
  private int searchOffset = 0;

  private final @NotNull Function<Path, ResourceFile> resourceFileFactory;
  public final boolean mimicJarUrlConnection;
  private final List<Loader> loaders = new ArrayList<>();

  private volatile boolean allUrlsWereProcessed;

  private final AtomicInteger lastLoaderProcessed = new AtomicInteger();
  private final ClasspathCache cache = new ClasspathCache();

  private final boolean useCache;
  final boolean isClassPathIndexEnabled;
  private final @Nullable CachePoolImpl cachePool;
  private final @Nullable Predicate<? super Path> cachingCondition;

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

  public interface ClassDataConsumer {
    boolean isByteBufferSupported(String name);

    Class<?> consumeClassData(String name, byte[] data) throws IOException;

    Class<?> consumeClassData(String name, ByteBuffer data) throws IOException;
  }

  public ClassPath(@NotNull Collection<Path> files,
                   @NotNull UrlClassLoader.Builder configuration,
                   @Nullable Function<Path, ResourceFile> resourceFileFactory,
                   boolean mimicJarUrlConnection) {
    useCache = configuration.useCache;
    cachePool = configuration.cachePool;
    cachingCondition = configuration.cachingCondition;
    isClassPathIndexEnabled = configuration.isClassPathIndexEnabled;
    this.mimicJarUrlConnection = mimicJarUrlConnection;

    this.files = files.toArray(new Path[]{});
    synchronized (this) {
      filesConvertedToDefaultFs = false;
    }
    if (resourceFileFactory == null) {
      this.resourceFileFactory = file -> new JdkZipResourceFile(file, configuration.lockJars);
    }
    else {
      this.resourceFileFactory = resourceFileFactory;
    }
  }

  public synchronized List<Path> getFiles() {
    if (!filesConvertedToDefaultFs) {
      try {
        FileSystem fs = FileSystems.getDefault();
        if (fs != UrlClassLoader.getPlatformDefaultFileSystem()) {
          Path[] newFiles = files.clone();
          for (int i = 0; i < files.length; i++) {
            final Path oldFile = files[i];
            if (oldFile.getFileSystem() == UrlClassLoader.getPlatformDefaultFileSystem()) {
              newFiles[i] = fs.getPath(oldFile.toString());
            }
          }
          files = newFiles;
        }
      }
      catch (Exception error) {
        throw new Error("Fatal error from class loader " + this, error);
      }
      filesConvertedToDefaultFs = true;
    }
    return Arrays.asList(files);
  }

  public synchronized void reset(Collection<Path> newClassPath) {
    reset();
    files = newClassPath.toArray(new Path[]{});
    filesConvertedToDefaultFs = false;
  }

  public synchronized void reset() {
    lastLoaderProcessed.set(0);
    allUrlsWereProcessed = false;
    loaders.clear();
    searchOffset = 0;
    cache.clearCache();
  }

  public static @NotNull Collection<Map.Entry<String, Path>> getLoadedClasses() {
    return new ArrayList<>(loadedClasses);
  }

  // in nanoseconds
  public static @NotNull Map<String, Long> getLoadingStats() {
    Map<String, Long> result = new HashMap<>(6);
    result.put("classLoadingTime", classLoading.timeCounter.get());
    result.put("classDefineTime", classDefineTotalTime.get());
    result.put("classRequests", (long)classLoading.requestCounter.get());

    result.put("resourceLoadingTime", resourceLoading.timeCounter.get());
    result.put("resourceRequests", (long)resourceLoading.requestCounter.get());

    result.put("identity", (long)ClassPath.class.hashCode());
    return result;
  }

  /** Adding URLs to classpath at runtime could lead to hard-to-debug errors */
  synchronized void addFile(@NotNull Path file) {
    for (Path existingFile : files) {
      if (existingFile.equals(file)) {
        return;
      }
    }

    Path[] result = Arrays.copyOf(files, files.length + 1);
    result[result.length - 1] = file;
    files = result;
    filesConvertedToDefaultFs = false;
    allUrlsWereProcessed = false;
  }

  /** Adding URLs to classpath at runtime could lead to hard-to-debug errors */
  // use only after approval
  public synchronized void addFiles(@NotNull Collection<Path> newList) {
    if (newList.isEmpty()) {
      return;
    }
    else if (newList.size() == 1) {
      addFile(newList instanceof List ? ((List<Path>)newList).get(0) : newList.iterator().next());
      return;
    }

    Set<Path> result = new LinkedHashSet<>(files.length + newList.size());
    Collections.addAll(result, files);
    result.addAll(newList);
    if (result.size() == files.length) {
      // no new files
      return;
    }

    files = result.toArray(new Path[]{});
    filesConvertedToDefaultFs = false;
    allUrlsWereProcessed = false;
  }

  public @Nullable Class<?> findClass(String className,
                                      String fileName,
                                      long packageNameHash,
                                      ClassDataConsumer classDataConsumer) throws IOException {
    long start = classLoading.startTiming();
    try {
      int i;
      if (useCache) {
        boolean allUrlsWereProcessedBeforeAccessingCache = allUrlsWereProcessed;
        int lastLoaderProcessedBeforeAccessingCache = lastLoaderProcessed.get();
        Loader[] loaders = cache.getClassLoadersByPackageNameHash(packageNameHash);
        if (loaders != null) {
          for (Loader loader : loaders) {
            Class<?> result = findClassInLoader(fileName, className, classDataConsumer, loader);
            if (result != null) {
              return result;
            }
          }
        }

        if (allUrlsWereProcessedBeforeAccessingCache) {
          if (isNewClassLoadingEnabled) {
            i = 0;
          }
          else {
            return null;
          }
        }
        else {
          i = lastLoaderProcessedBeforeAccessingCache;
        }
      }
      else {
        i = 0;
      }
      return findClassWithoutCache(className, fileName, i, classDataConsumer);
    }
    finally {
      classLoading.record(start, className);
    }
  }

  private @Nullable Class<?> findClassWithoutCache(String className,
                                                   String fileName,
                                                   int initialLoaderIndex,
                                                   ClassDataConsumer classDataConsumer) throws IOException {
    for (int loaderIndex = initialLoaderIndex; ; loaderIndex++) {
      Loader loader = loaderIndex < lastLoaderProcessed.get() ? loaders.get(loaderIndex) : getLoaderSlowPath(loaderIndex);
      if (loader == null) {
        return null;
      }

      Class<?> result = findClassInLoader(fileName, className, classDataConsumer, loader);
      if (result != null) {
        return result;
      }
    }
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
      loadedClasses.add(new AbstractMap.SimpleImmutableEntry<>(fileName, loader.getPath()));
    }
    return result;
  }

  public @Nullable Resource findResource(@NotNull String resourceName) {
    long start = resourceLoading.startTiming();
    try {
      int i;
      if (useCache) {
        boolean allUrlsWereProcessedBeforeAccessingCache = allUrlsWereProcessed;
        i = lastLoaderProcessed.get();
        Loader[] loaders = cache.getLoadersByName(resourceName);
        if (loaders != null) {
          for (Loader loader : loaders) {
            Resource resource = loader.getResource(resourceName);
            if (resource != null) {
              if (loadedClasses != null) {
                loadedClasses.add(new AbstractMap.SimpleImmutableEntry<>(resourceName, loader.getPath()));
              }
              return resource;
            }
          }
        }

        if (allUrlsWereProcessedBeforeAccessingCache) {
          return null;
        }
      }
      else {
        i = 0;
      }

      Loader loader;
      while ((loader = getLoader(i++)) != null) {
        Resource resource = loader.getResource(resourceName);
        if (resource != null) {
          if (loadedClasses != null) {
            loadedClasses.add(new AbstractMap.SimpleImmutableEntry<>(resourceName, loader.getPath()));
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
      Loader[] loaders = cache.getLoadersByResourcePackageDir(dir);
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

  private @Nullable Loader getLoader(int loaderIndex) {
    // volatile read
    return loaderIndex < lastLoaderProcessed.get() ? loaders.get(loaderIndex) : getLoaderSlowPath(loaderIndex);
  }

  private synchronized @Nullable Loader getLoaderSlowPath(int loaderIndex) {
    while (loaders.size() < (loaderIndex + 1)) {
      if (searchOffset == files.length) {
        if (useCache) {
          allUrlsWereProcessed = true;
        }
        return null;
      }

      // https://youtrack.jetbrains.com/issue/IDEA-314175
      // some environments (e.g., Bazel tests) put relative jar paths on the Java classpath,
      // because relative paths are useful for hermeticity.
      Path path = files[searchOffset++].toAbsolutePath();
      try {
        Loader loader = createLoader(path);
        if (loader != null) {
          if (useCache && searchOffset == files.length) {
            allUrlsWereProcessed = true;
          }

          loaders.add(loader);
          lastLoaderProcessed.incrementAndGet();
        }
      }
      catch (IOException e) {
        //noinspection CallToPrintStackTrace
        e.printStackTrace();
      }
    }

    return loaders.get(loaderIndex);
  }

  // TODO: synchronized should not be needed
  public synchronized @NotNull List<Path> getBaseUrls() {
    List<Path> result = new ArrayList<>();
    for (Loader loader : loaders) {
      result.add(loader.getPath());
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
    catch (RuntimeException e) {
      throw new RuntimeException("Failed to read attributes of file from " + file.getFileSystem(), e);
    }

    if (fileAttributes.isDirectory()) {
      return useCache && !isNewClassLoadingEnabled
             ? FileLoader.createCachingFileLoader(file, cachePool, cachingCondition, isClassPathIndexEnabled, cache)
             : new FileLoader(file);
    }
    else if (!fileAttributes.isRegularFile()) {
      return null;
    }

    ResourceFile zipFile = resourceFileFactory.apply(file);
    JarLoader loader = new JarLoader(file, this, zipFile);
    if (useCache) {
      ClasspathCache.IndexRegistrar data = cachePool == null ? null : cachePool.loaderIndexCache.get(file);
      if (data == null) {
        data = zipFile.buildClassPathCacheData();
        if (cachePool != null && cachingCondition != null && cachingCondition.test(file)) {
          cachePool.loaderIndexCache.put(file, data);
        }
      }
      cache.applyLoaderData(data, loader);
    }

    String filePath = file.toString();
    if (filePath.startsWith(CLASSPATH_JAR_FILE_NAME_PREFIX, filePath.lastIndexOf(File.separatorChar) + 1)) {
      addFromManifestClassPathIfNeeded(file, zipFile, loader);
    }
    return loader;
  }

  private void addFromManifestClassPathIfNeeded(@NotNull Path file, ResourceFile zipFile, JarLoader loader) {
    String[] referencedJars = loadManifestClasspath(loader, zipFile);
    if (referencedJars != null) {
      long startReferenced = logLoadingInfo ? System.nanoTime() : 0;
      List<Path> files = new ArrayList<>(referencedJars.length);
      for (String referencedJar : referencedJars) {
        try {
          files.add(Paths.get(UrlClassLoader.urlToFilePath(referencedJar)));
        }
        catch (Exception e) {
          //noinspection UseOfSystemOutOrSystemErr
          System.err.println("file: " + file + " / " + referencedJar + " " + e);
        }
      }
      addFiles(files);
      if (logLoadingInfo) {
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println("Loaded all " + referencedJars.length + " files " + (System.nanoTime() - startReferenced) / 1000000 + "ms");
      }
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

  private String @Nullable [] loadManifestClasspath(@NotNull JarLoader loader, @NotNull ResourceFile zipFile) {
    try {
      Map<JarLoader.Attribute, String> result = useCache && cachePool != null ? cachePool.getManifestData(loader.getPath()) : null;
      if (result == null) {
        Attributes manifestAttributes = zipFile.loadManifestAttributes();
        result = manifestAttributes == null ? Collections.emptyMap() : JarLoader.getAttributes(manifestAttributes);
        if (useCache && cachePool != null && cachingCondition != null && cachingCondition.test(loader.getPath())) {
          cachePool.cacheManifestData(loader.getPath(), result);
        }
      }
      String classPath = result.get(JarLoader.Attribute.CLASS_PATH);
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

  static final class MeasuringClassDataConsumer implements ClassDataConsumer {
    private static final ThreadLocal<Boolean> doingClassDefineTiming = new ThreadLocal<>();

    private final ClassDataConsumer classDataConsumer;

    MeasuringClassDataConsumer(ClassDataConsumer classDataConsumer) {
      this.classDataConsumer = classDataConsumer;
    }

    @Override
    public boolean isByteBufferSupported(String name) {
      return classDataConsumer.isByteBufferSupported(name);
    }

    @Override
    public Class<?> consumeClassData(String name, byte[] data) throws IOException {
      long start = startTiming();
      try {
        return classDataConsumer.consumeClassData(name, data);
      }
      finally {
        record(start);
      }
    }

    @Override
    public Class<?> consumeClassData(String name, ByteBuffer data) throws IOException {
      long start = startTiming();
      try {
        return classDataConsumer.consumeClassData(name, data);
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
        //noinspection ThreadLocalSetWithNull
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

      //noinspection ThreadLocalSetWithNull
      doingTiming.set(null);

      long time = System.nanoTime() - start;
      long totalTime = timeCounter.addAndGet(time);
      int totalRequests = requestCounter.incrementAndGet();
      if (logLoadingInfo) {
        if (time > 3_000_000L) {
          System.out.println(TimeUnit.NANOSECONDS.toMillis(time) + " ms for " + resourceName);
        }
        if (totalRequests % 10_000 == 0) {
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
