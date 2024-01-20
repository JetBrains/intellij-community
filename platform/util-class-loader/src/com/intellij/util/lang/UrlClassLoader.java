// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A class loader which allows for various customizations, e.g., not locking jars or using a special cache to speed up class loading.
 * Should be constructed using {@link #build()} method.
 * <p>
 * This classloader implementation is separate from {@link PathClassLoader} because it's used in runtime modules with JDK 1.8.
 */
public class UrlClassLoader extends ClassLoader implements ClassPath.ClassDataConsumer {
  public static final String CLASSPATH_INDEX_PROPERTY_NAME = "idea.classpath.index.enabled";

  private static final boolean isClassPathIndexEnabledGlobalValue =
    Boolean.parseBoolean(System.getProperty(CLASSPATH_INDEX_PROPERTY_NAME, "false"));

  private static final boolean mimicJarUrlConnection = Boolean.parseBoolean(System.getProperty("idea.mimic.jar.url.connection", "false"));

  private static final boolean isParallelCapable = registerAsParallelCapable();
  private static final ClassLoader appClassLoader = UrlClassLoader.class.getClassLoader();

  private static final ThreadLocal<Boolean> skipFindingResource = new ThreadLocal<>();

  protected final ClassPath classPath;
  private final ClassLoadingLocks classLoadingLocks;
  private final boolean isBootstrapResourcesAllowed;
  private final boolean isSystemClassLoader;

  private final boolean enableCoroutineDump;

  protected final @NotNull ClassPath.ClassDataConsumer classDataConsumer =
    ClassPath.recordLoadingTime ? new ClassPath.MeasuringClassDataConsumer(this) : this;

  /**
   * Called by the VM to support dynamic additions to the class path.
   *
   * @see java.lang.instrument.Instrumentation#appendToSystemClassLoaderSearch
   */
  @SuppressWarnings("unused")
  final void appendToClassPathForInstrumentation(@NotNull String jar) {
    classPath.addFile(Paths.get(jar));
  }

  /**
   * There are two definitions of the `ClassPath` class: one from the app class loader that is used by bootstrap,
   * and another one from the core class loader produced as a result of creating a plugin class loader.
   * The core class loader doesn't use bootstrap class loader as a parent - instead, only platform classloader is used (only JRE classes).
   */
  @ApiStatus.Internal
  public final @NotNull ClassPath getClassPath() {
    return classPath;
  }

  /**
   * See com.intellij.TestAll#getClassRoots()
   */
  public final @NotNull List<Path> getBaseUrls() {
    return classPath.getBaseUrls();
  }

  // called via reflection
  @SuppressWarnings({"unused", "MethodMayBeStatic"})
  public final @NotNull Map<String, Long> getLoadingStats() {
    return ClassPath.getLoadingStats();
  }

  public static @NotNull UrlClassLoader.Builder build() {
    return new Builder();
  }

  /** @deprecated use {@link #build()} (left for compatibility with `java.system.class.loader` setting) */
  @Deprecated
  public UrlClassLoader(@NotNull ClassLoader parent) {
    this(createDefaultBuilderForJdk(parent), null, isParallelCapable);

    registerInClassLoaderValueMap(parent, this);
  }

  protected static void registerInClassLoaderValueMap(@NotNull ClassLoader parent, @NotNull ClassLoader classLoader) {
    // without this ToolProvider.getSystemJavaCompiler() does not work in jdk 9+
    try {
      Field f = ClassLoader.class.getDeclaredField("classLoaderValueMap");
      f.setAccessible(true);
      f.set(classLoader, f.get(parent));
    }
    catch (Exception ignored) {
    }
  }

  protected static @NotNull UrlClassLoader.Builder createDefaultBuilderForJdk(@NotNull ClassLoader parent) {
    Builder configuration = new Builder();

    if (parent instanceof URLClassLoader) {
      URL[] urls = ((URLClassLoader)parent).getURLs();
      // LinkedHashSet is used to remove duplicates
      Set<Path> files = new LinkedHashSet<>(urls.length);
      for (URL url : urls) {
        files.add(Paths.get(url.getPath()));
      }
      configuration.files = files;
    }
    else {
      String[] parts = System.getProperty("java.class.path").split(File.pathSeparator);
      Set<Path> files = new LinkedHashSet<>(parts.length);
      for (String s : parts) {
        files.add(Paths.get(s));
      }
      configuration.files = files;
    }

    configuration.isSystemClassLoader = true;
    configuration.parent = parent.getParent();
    configuration.useCache = true;
    configuration.isClassPathIndexEnabled = isClassPathIndexEnabledGlobalValue;
    configuration.isBootstrapResourcesAllowed = Boolean.parseBoolean(System.getProperty("idea.allow.bootstrap.resources", "true"));
    return configuration;
  }

  protected UrlClassLoader(@NotNull UrlClassLoader.Builder builder, boolean isParallelCapable) {
    this(builder, null, isParallelCapable);
  }

  protected UrlClassLoader(@NotNull UrlClassLoader.Builder builder,
                           @Nullable Function<Path, ResourceFile> resourceFileFactory,
                           boolean isParallelCapable) {
    super(builder.parent);

    isSystemClassLoader = builder.isSystemClassLoader;

    enableCoroutineDump = Boolean.parseBoolean(System.getProperty("idea.enable.coroutine.dump.using.classloader", "true"));
    classPath = new ClassPath(builder.files, builder, resourceFileFactory, mimicJarUrlConnection);

    isBootstrapResourcesAllowed = builder.isBootstrapResourcesAllowed;
    classLoadingLocks = isParallelCapable ? new ClassLoadingLocks() : null;
  }

  protected UrlClassLoader(@NotNull ClassPath classPath) {
    super(null);

    this.classPath = classPath;
    isBootstrapResourcesAllowed = false;
    isSystemClassLoader = false;
    classLoadingLocks = new ClassLoadingLocks();
    enableCoroutineDump = false;
  }

  /** @deprecated adding URLs to a classloader at runtime could lead to hard-to-debug errors */
  @Deprecated
  public final void addURL(@NotNull URL url) {
    classPath.addFile(Paths.get(url.getPath()));
  }

  /**
   * @deprecated Do not use.
   * Internal method, used via method handle by `configureUsingIdeaClassloader` (see ClassLoaderConfigurator).
   */
  @ApiStatus.Internal
  @Deprecated
  public final void addFiles(@NotNull List<Path> files) {
    classPath.addFiles(files);
  }

  public final @NotNull List<URL> getUrls() {
    List<URL> result = new ArrayList<>();
    for (Path file : classPath.getFiles()) {
      try {
        result.add(file.toUri().toURL());
      }
      catch (MalformedURLException ignored) { }
    }
    return result;
  }

  public final @NotNull List<Path> getFiles() {
    return classPath.getFiles();
  }

  public boolean hasLoadedClass(String name) {
    Class<?> aClass = findLoadedClass(name);
    return aClass != null && aClass.getClassLoader() == this;
  }

  @Override
  protected Class<?> findClass(@NotNull String name) throws ClassNotFoundException {
    String fileNameWithoutExtension = name.replace('.', '/');
    String fileName = fileNameWithoutExtension + ClasspathCache.CLASS_EXTENSION;
    long packageNameHash = ClasspathCache.getPackageNameHash(fileNameWithoutExtension, fileNameWithoutExtension.lastIndexOf('/'));

    // When used as a system classloader (java.system.class.loader),
    // the UrlClassLoader class has to be loaded together with its dependencies by the AppClassLoader.
    // The same dependencies might be used in the platform and in plugins.
    // If a class is loaded together with UrlClassLoader class by AppClassLoader, and again by UrlClassLoader,
    // then it gets defined twice, which leads to CCEs later.
    // To avoid double-loading, the loading of a select number of packages is delegated to AppClassLoader.
    //
    // com.intellij.util.lang org.jetbrains.ikv
    // see XxHash3Test.packages
    if (isSystemClassLoader && (packageNameHash == -9217824570049207139L || packageNameHash == -1976620678582843062L)) {
      // these two classes from com.intellij.util.lang are located in intellij.platform.util module, which shouldn't be loaded by appClassLoader (IDEA-331043)
      if (!fileNameWithoutExtension.endsWith("/CompoundRuntimeException") && !fileNameWithoutExtension.endsWith("/JavaVersion")) {
        return appClassLoader.loadClass(name);
      }
    }

    Class<?> clazz;
    try {
      if (enableCoroutineDump && packageNameHash == -3930079881136890558L && name.equals("kotlin.coroutines.jvm.internal.DebugProbesKt")) {
        return classDataConsumer.consumeClassData(name, DEBUG_PROBES_CLASS);
      }

      clazz = classPath.findClass(name, fileName, packageNameHash, classDataConsumer);
    }
    catch (IOException e) {
      throw new ClassNotFoundException(name, e);
    }
    if (clazz == null) {
      throw new ClassNotFoundException(name);
    }
    return clazz;
  }

  private void definePackageIfNeeded(String name) {
    int lastDotIndex = name.lastIndexOf('.');
    if (lastDotIndex == -1) {
      return;
    }

    String packageName = name.substring(0, lastDotIndex);
    // check if the package is already loaded
    if (isPackageDefined(packageName)) {
      return;
    }

    try {
      definePackage(packageName, null, null, null, null, null, null, null);
    }
    catch (IllegalArgumentException ignore) {
      // do nothing, the package is already defined by another thread
    }
  }

  protected boolean isPackageDefined(String packageName) {
    return getPackage(packageName) != null;
  }

  @Override
  public boolean isByteBufferSupported(@NotNull String name) {
    return true;
  }

  @Override
  public Class<?> consumeClassData(@NotNull String name, byte[] data) throws IOException {
    definePackageIfNeeded(name);
    return super.defineClass(name, data, 0, data.length, null);
  }

  @Override
  public Class<?> consumeClassData(@NotNull String name, ByteBuffer data) {
    definePackageIfNeeded(name);
    return super.defineClass(name, data, null);
  }

  @Override
  public @Nullable URL findResource(@NotNull String name) {
    if (skipFindingResource.get() != null) {
      return null;
    }
    Resource resource = doFindResource(name);
    return resource != null ? resource.getURL() : null;
  }

  public byte @Nullable [] getResourceAsBytes(@NotNull String name, boolean checkParents) throws IOException {
    Resource resource = classPath.findResource(name);
    return resource == null ? null : resource.getBytes();
  }

  @Override
  public @Nullable InputStream getResourceAsStream(@NotNull String name) {
    Resource resource = doFindResource(name);
    if (resource != null) {
      try {
        return resource.getInputStream();
      }
      catch (IOException e) {
        logError("Cannot load resource " + name, e);
        return null;
      }
    }

    if (isBootstrapResourcesAllowed) {
      skipFindingResource.set(Boolean.TRUE);
      try {
        URL url = super.getResource(name);
        if (url != null) {
          try {
            return url.openStream();
          }
          catch (IOException ignore) { }
        }
      }
      finally {
        //noinspection ThreadLocalSetWithNull
        skipFindingResource.set(null);
      }
    }

    return null;
  }

  private @Nullable Resource doFindResource(String name) {
    String canonicalPath = toCanonicalPath(name);
    Resource resource = classPath.findResource(canonicalPath);
    if (resource == null && canonicalPath.startsWith("/") && classPath.findResource(canonicalPath.substring(1)) != null) {
      // reporting malformed paths only when there's a resource at the right one - which is rarely the case
      // (see also `PluginClassLoader#doFindResource`)
      logError("Calling `ClassLoader#getResource` with leading slash doesn't work; strip", new IllegalArgumentException(name));
    }
    return resource;
  }

  public final void processResources(@NotNull String dir,
                                     @NotNull Predicate<? super String> fileNameFilter,
                                     @NotNull BiConsumer<? super String, ? super InputStream> consumer) throws IOException {
    classPath.processResources(dir, fileNameFilter, consumer);
  }

  @Override
  public @NotNull Enumeration<URL> findResources(@NotNull String name) {
    return classPath.getResources(name);
  }

  @Override
  protected final @NotNull Object getClassLoadingLock(String className) {
    return classLoadingLocks == null ? this : classLoadingLocks.getOrCreateLock(className);
  }

  @ApiStatus.Internal
  public @Nullable BiFunction<String, Boolean, String> resolveScopeManager;

  public @Nullable Class<?> loadClassInsideSelf(String name,
                                                String fileName,
                                                long packageNameHash,
                                                boolean forceLoadFromSubPluginClassloader) throws IOException {
    synchronized (getClassLoadingLock(name)) {
      Class<?> c = findLoadedClass(name);
      if (c != null) {
        return c;
      }

      if (!forceLoadFromSubPluginClassloader) {
        // "self" makes sense for PluginClassLoader, but not for UrlClassLoader - our parent it is implementation detail
        ClassLoader parent = getParent();
        if (parent != null) {
          try {
            c = parent.loadClass(name);
          }
          catch (ClassNotFoundException ignore) { }
        }

        if (c != null) {
          return c;
        }
      }
      return classPath.findClass(name, fileName, packageNameHash, classDataConsumer);
    }
  }

  /**
   * An interface for a pool to store internal caches that can be shared between different class loaders
   * if they contain the same URLs in their class paths.
   * <p>
   * The implementation is subject to change; one shouldn't rely on it.
   *
   * @see #createCachePool()
   * @see Builder#useCache
   */
  public interface CachePool { }

  /**
   * @return a new pool to be able to share internal caches between different class loaders if they contain the same URLs
   * in their class paths.
   */
  public static @NotNull CachePool createCachePool() {
    return new CachePoolImpl();
  }

  @SuppressWarnings("DuplicatedCode")
  protected static String toCanonicalPath(@NotNull String path) {
    if (path.isEmpty()) {
      return path;
    }

    if (path.charAt(0) == '.') {
      if (path.length() == 1) {
        return "";
      }
      char c = path.charAt(1);
      if (c == '/') {
        path = path.substring(2);
      }
    }

    // trying to speed up the common case when there are no "//" or "/."
    int index = -1;
    do {
      index = path.indexOf('/', index + 1);
      char next = index == path.length() - 1 ? 0 : path.charAt(index + 1);
      if (next == '.' || next == '/') {
        break;
      }
    }
    while (index != -1);
    if (index == -1) {
      return path;
    }

    StringBuilder result = new StringBuilder(path.length());
    int start = processRoot(path, result);
    int dots = 0;
    boolean separator = true;

    for (int i = start; i < path.length(); ++i) {
      char c = path.charAt(i);
      if (c == '/') {
        if (!separator) {
          processDots(result, dots, start);
          dots = 0;
        }
        separator = true;
      }
      else if (c == '.') {
        if (separator || dots > 0) {
          ++dots;
        }
        else {
          result.append('.');
        }
        separator = false;
      }
      else {
        while (dots > 0) {
          result.append('.');
          dots--;
        }
        result.append(c);
        separator = false;
      }
    }

    if (dots > 0) {
      processDots(result, dots, start);
    }
    return result.toString();
  }

  @SuppressWarnings("SameParameterValue")
  private static boolean endsWith(@NotNull CharSequence text, @NotNull CharSequence suffix) {
    int l1 = text.length();
    int l2 = suffix.length();
    if (l1 < l2) return false;

    for (int i = l1 - 1; i >= l1 - l2; i--) {
      if (text.charAt(i) != suffix.charAt(i + l2 - l1)) return false;
    }

    return true;
  }

  @SuppressWarnings("SameParameterValue")
  private static int lastIndexOf(@NotNull CharSequence s, char c, int start, int end) {
    start = Math.max(start, 0);
    for (int i = Math.min(end, s.length()) - 1; i >= start; i--) {
      if (s.charAt(i) == c) return i;
    }
    return -1;
  }

  @SuppressWarnings("DuplicatedCode")
  private static void processDots(StringBuilder result, int dots, int start) {
    if (dots == 2) {
      int pos = -1;
      if (!endsWith(result, "/../") && !"../".contentEquals(result)) {
        pos = lastIndexOf(result, '/', start, result.length() - 1);
        if (pos >= 0) {
          ++pos;  // separator found, trim to next char
        }
        else if (start > 0) {
          pos = start;  // the path is absolute, trim to root ('/..' -> '/')
        }
        else if (result.length() > 0) {
          pos = 0;  // the path is relative, trim to default ('a/..' -> '')
        }
      }
      if (pos >= 0) {
        result.delete(pos, result.length());
      }
      else {
        result.append("../");  // impossible to traverse, keep as-is
      }
    }
    else if (dots != 1) {
      for (int i = 0; i < dots; i++) {
        result.append('.');
      }
      result.append('/');
    }
  }

  @SuppressWarnings("DuplicatedCode")
  private static int processRoot(String path, StringBuilder result) {
    if (!path.isEmpty() && path.charAt(0) == '/') {
      result.append('/');
      return 1;
    }

    if (path.length() > 2 && path.charAt(1) == ':' && path.charAt(2) == '/') {
      result.append(path, 0, 3);
      return 3;
    }

    return 0;
  }

  @SuppressWarnings({"UseOfSystemOutOrSystemErr", "SameParameterValue"})
  private void logError(String message, Throwable t) {
    try {
      Class<?> logger = loadClass("com.intellij.openapi.diagnostic.Logger");
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      Object instance = lookup.findStatic(logger, "getInstance", MethodType.methodType(logger, Class.class)).invoke(getClass());
      lookup.findVirtual(logger, "error", MethodType.methodType(void.class, String.class, Throwable.class))
        .bindTo(instance)
        .invokeExact(message, t);
    }
    catch (Throwable tt) {
      t.addSuppressed(tt);
      System.err.println(getClass().getName() + ": " +  message);
      t.printStackTrace(System.err);
    }
  }

  // work around corrupted URLs produced by File.getURL()
  // public for test
  public static @NotNull String urlToFilePath(@NotNull String url) {
    int start = url.startsWith("file:") ? "file:".length() : 0;
    int end = url.indexOf("!/");
    if (url.charAt(start) == '/') {
      // trim leading slashes before drive letter
      if (url.length() > (start + 2) && url.charAt(start + 2) == ':') {
        start++;
      }
    }
    return UrlUtilRt.unescapePercentSequences(url, start, end < 0 ? url.length() : end).toString();
  }

  public static final class Builder {
    Collection<Path> files = Collections.emptyList();
    ClassLoader parent;
    boolean lockJars = true;
    boolean useCache = true;
    boolean isSystemClassLoader;
    boolean isClassPathIndexEnabled = isClassPathIndexEnabledGlobalValue;
    boolean isBootstrapResourcesAllowed;
    @Nullable CachePoolImpl cachePool;
    Predicate<? super Path> cachingCondition;

    Builder() { }

    /**
     * @deprecated Use {@link #files(List)}. Using of {@link URL} is discouraged in favor of modern {@link Path}.
     */
    @ApiStatus.ScheduledForRemoval
    @Deprecated
    public @NotNull UrlClassLoader.Builder urls(@NotNull List<URL> urls) {
      List<Path> files = new ArrayList<>(urls.size());
      for (URL url : urls) {
        files.add(Paths.get(urlToFilePath(url.getPath())));
      }
      this.files = files;
      return this;
    }

    public @NotNull UrlClassLoader.Builder files(@NotNull List<Path> paths) {
      this.files = paths;
      return this;
    }

    public @NotNull UrlClassLoader.Builder parent(ClassLoader parent) {
      this.parent = parent;
      return this;
    }

    /**
     * `ZipFile` handles opened in `JarLoader` will be kept in as soft references.
     * Depending on OS, the option significantly speeds up classloading from libraries.
     * Caveat: on Windows, an unclosed handle locks a file, preventing its modification.
     * Thus, the option is recommended when .jar files are not modified or a process that uses this option is transient.
     */
    public @NotNull UrlClassLoader.Builder allowLock(boolean lockJars) {
      this.lockJars = lockJars;
      return this;
    }

    /**
     * Build a backward index of packages to class/resource names; allows reducing I/O during classloading.
     */
    public @NotNull UrlClassLoader.Builder useCache() {
      useCache = true;
      return this;
    }

    public @NotNull UrlClassLoader.Builder useCache(boolean useCache) {
      this.useCache = useCache;
      return this;
    }

    /**
     * `FileLoader` will save a list of files/packages under its root and use this information instead of walking files.
     * Should be used only when the caches can be properly invalidated (when e.g., a new file appears under `FileLoader`'s root).
     * Currently, the flag is used for faster unit tests / debug IDE instance, because IDEA's build process (as of 14.1) ensures deletion of
     * such information upon appearing new file for output root.
     * <p>
     * IDEA's building process does not ensure deletion of cached information upon deletion of some file under a local root,
     * but false positives are not a logical error, since code is prepared for that and disk access is performed upon class/resource loading.
     */
    public @NotNull UrlClassLoader.Builder usePersistentClasspathIndexForLocalClassDirectories() {
      this.isClassPathIndexEnabled = true;
      return this;
    }

    /**
     * Requests the class loader being built to use a cache and, if possible, retrieve and store the cached data from a special cache pool
     * that can be shared between several loaders.
     *
     * @param pool      cache pool
     * @param condition a custom policy to provide a possibility to prohibit caching for some URLs.
     */
    public @NotNull UrlClassLoader.Builder useCache(@NotNull UrlClassLoader.CachePool pool, @NotNull Predicate<? super Path> condition) {
      useCache = true;
      cachePool = (CachePoolImpl)pool;
      cachingCondition = condition;
      return this;
    }

    public @NotNull UrlClassLoader.Builder noPreload() {
      return this;
    }

    public @NotNull UrlClassLoader.Builder allowBootstrapResources() {
      return allowBootstrapResources(true);
    }

    public @NotNull UrlClassLoader.Builder allowBootstrapResources(boolean allowBootstrapResources) {
      isBootstrapResourcesAllowed = allowBootstrapResources;
      return this;
    }

    public @NotNull UrlClassLoader get() {
      return new UrlClassLoader(this, null, isParallelCapable);
    }
  }

  private static final byte[] DEBUG_PROBES_CLASS =
    new byte[]{-54, -2, -70, -66, 0, 0, 0, 52, 0, 63, 1, 0, 44, 107, 111, 116, 108, 105, 110, 47, 99, 111, 114, 111, 117, 116, 105, 110,
      101, 115, 47, 106, 118, 109, 47, 105, 110, 116, 101, 114, 110, 97, 108, 47, 68, 101, 98, 117, 103, 80, 114, 111, 98, 101, 115, 75,
      116, 7, 0, 1, 1, 0, 16, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 79, 98, 106, 101, 99, 116, 7, 0, 3, 1, 0, 21, 112, 114, 111, 98,
      101, 67, 111, 114, 111, 117, 116, 105, 110, 101, 67, 114, 101, 97, 116, 101, 100, 1, 0, 66, 40, 76, 107, 111, 116, 108, 105, 110, 47,
      99, 111, 114, 111, 117, 116, 105, 110, 101, 115, 47, 67, 111, 110, 116, 105, 110, 117, 97, 116, 105, 111, 110, 59, 41, 76, 107, 111,
      116, 108, 105, 110, 47, 99, 111, 114, 111, 117, 116, 105, 110, 101, 115, 47, 67, 111, 110, 116, 105, 110, 117, 97, 116, 105, 111, 110,
      59, 1, 0, 99, 60, 84, 58, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 79, 98, 106, 101, 99, 116, 59, 62, 40, 76, 107, 111, 116,
      108, 105, 110, 47, 99, 111, 114, 111, 117, 116, 105, 110, 101, 115, 47, 67, 111, 110, 116, 105, 110, 117, 97, 116, 105, 111, 110, 60,
      45, 84, 84, 59, 62, 59, 41, 76, 107, 111, 116, 108, 105, 110, 47, 99, 111, 114, 111, 117, 116, 105, 110, 101, 115, 47, 67, 111, 110,
      116, 105, 110, 117, 97, 116, 105, 111, 110, 60, 84, 84, 59, 62, 59, 1, 0, 35, 76, 111, 114, 103, 47, 106, 101, 116, 98, 114, 97, 105,
      110, 115, 47, 97, 110, 110, 111, 116, 97, 116, 105, 111, 110, 115, 47, 78, 111, 116, 78, 117, 108, 108, 59, 1, 0, 10, 99, 111, 109,
      112, 108, 101, 116, 105, 111, 110, 8, 0, 9, 1, 0, 30, 107, 111, 116, 108, 105, 110, 47, 106, 118, 109, 47, 105, 110, 116, 101, 114,
      110, 97, 108, 47, 73, 110, 116, 114, 105, 110, 115, 105, 99, 115, 7, 0, 11, 1, 0, 21, 99, 104, 101, 99, 107, 78, 111, 116, 78, 117,
      108, 108, 80, 97, 114, 97, 109, 101, 116, 101, 114, 1, 0, 39, 40, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 79, 98, 106, 101,
      99, 116, 59, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 83, 116, 114, 105, 110, 103, 59, 41, 86, 12, 0, 13, 0, 14, 10, 0, 12, 0,
      15, 1, 0, 49, 107, 111, 116, 108, 105, 110, 120, 47, 99, 111, 114, 111, 117, 116, 105, 110, 101, 115, 47, 100, 101, 98, 117, 103, 47,
      105, 110, 116, 101, 114, 110, 97, 108, 47, 68, 101, 98, 117, 103, 80, 114, 111, 98, 101, 115, 73, 109, 112, 108, 7, 0, 17, 1, 0, 8,
      73, 78, 83, 84, 65, 78, 67, 69, 1, 0, 51, 76, 107, 111, 116, 108, 105, 110, 120, 47, 99, 111, 114, 111, 117, 116, 105, 110, 101, 115,
      47, 100, 101, 98, 117, 103, 47, 105, 110, 116, 101, 114, 110, 97, 108, 47, 68, 101, 98, 117, 103, 80, 114, 111, 98, 101, 115, 73, 109,
      112, 108, 59, 12, 0, 19, 0, 20, 9, 0, 18, 0, 21, 1, 0, 45, 112, 114, 111, 98, 101, 67, 111, 114, 111, 117, 116, 105, 110, 101, 67,
      114, 101, 97, 116, 101, 100, 36, 107, 111, 116, 108, 105, 110, 120, 95, 99, 111, 114, 111, 117, 116, 105, 110, 101, 115, 95, 99, 111,
      114, 101, 12, 0, 23, 0, 6, 10, 0, 18, 0, 24, 1, 0, 32, 76, 107, 111, 116, 108, 105, 110, 47, 99, 111, 114, 111, 117, 116, 105, 110,
      101, 115, 47, 67, 111, 110, 116, 105, 110, 117, 97, 116, 105, 111, 110, 59, 1, 0, 21, 112, 114, 111, 98, 101, 67, 111, 114, 111, 117,
      116, 105, 110, 101, 82, 101, 115, 117, 109, 101, 100, 1, 0, 35, 40, 76, 107, 111, 116, 108, 105, 110, 47, 99, 111, 114, 111, 117, 116,
      105, 110, 101, 115, 47, 67, 111, 110, 116, 105, 110, 117, 97, 116, 105, 111, 110, 59, 41, 86, 1, 0, 38, 40, 76, 107, 111, 116, 108,
      105, 110, 47, 99, 111, 114, 111, 117, 116, 105, 110, 101, 115, 47, 67, 111, 110, 116, 105, 110, 117, 97, 116, 105, 111, 110, 60, 42,
      62, 59, 41, 86, 1, 0, 5, 102, 114, 97, 109, 101, 8, 0, 30, 1, 0, 45, 112, 114, 111, 98, 101, 67, 111, 114, 111, 117, 116, 105, 110,
      101, 82, 101, 115, 117, 109, 101, 100, 36, 107, 111, 116, 108, 105, 110, 120, 95, 99, 111, 114, 111, 117, 116, 105, 110, 101, 115, 95,
      99, 111, 114, 101, 12, 0, 32, 0, 28, 10, 0, 18, 0, 33, 1, 0, 23, 112, 114, 111, 98, 101, 67, 111, 114, 111, 117, 116, 105, 110, 101,
      83, 117, 115, 112, 101, 110, 100, 101, 100, 1, 0, 47, 112, 114, 111, 98, 101, 67, 111, 114, 111, 117, 116, 105, 110, 101, 83, 117,
      115, 112, 101, 110, 100, 101, 100, 36, 107, 111, 116, 108, 105, 110, 120, 95, 99, 111, 114, 111, 117, 116, 105, 110, 101, 115, 95, 99,
      111, 114, 101, 12, 0, 36, 0, 28, 10, 0, 18, 0, 37, 1, 0, 17, 76, 107, 111, 116, 108, 105, 110, 47, 77, 101, 116, 97, 100, 97, 116, 97,
      59, 1, 0, 2, 109, 118, 3, 0, 0, 0, 1, 3, 0, 0, 0, 8, 3, 0, 0, 0, 0, 1, 0, 1, 107, 3, 0, 0, 0, 2, 1, 0, 2, 120, 105, 3, 0, 0, 0, 48, 1,
      0, 2, 100, 49, 1, 0, 111, -64, -128, 18, 10, -64, -128, 10, 2, 24, 2, 10, 2, 8, 3, 10, 2, 16, 2, 10, 2, 8, 3, 26, 34, 16, -64, -128,
      26, 8, 18, 4, 18, 2, 72, 2, 48, 1, 34, 4, 8, -64, -128, 16, 2, 50, 12, 16, 3, 26, 8, 18, 4, 18, 2, 72, 2, 48, 1, 72, -64, -128, 26,
      20, 16, 4, 26, 2, 48, 5, 50, 10, 16, 6, 26, 6, 18, 2, 8, 3, 48, 1, 72, -64, -128, 26, 20, 16, 7, 26, 2, 48, 5, 50, 10, 16, 6, 26, 6,
      18, 2, 8, 3, 48, 1, 72, -64, -128, -62, -88, 6, 8, 1, 0, 2, 100, 50, 1, 0, 1, 84, 1, 0, 0, 1, 0, 53, 107, 111, 116, 108, 105, 110,
      120, 45, 99, 111, 114, 111, 117, 116, 105, 110, 101, 115, 45, 105, 110, 116, 101, 103, 114, 97, 116, 105, 111, 110, 45, 116, 101, 115,
      116, 105, 110, 103, 95, 100, 101, 98, 117, 103, 65, 103, 101, 110, 116, 84, 101, 115, 116, 1, 0, 14, 68, 101, 98, 117, 103, 80, 114,
      111, 98, 101, 115, 46, 107, 116, 1, 0, 4, 67, 111, 100, 101, 1, 0, 15, 76, 105, 110, 101, 78, 117, 109, 98, 101, 114, 84, 97, 98, 108,
      101, 1, 0, 18, 76, 111, 99, 97, 108, 86, 97, 114, 105, 97, 98, 108, 101, 84, 97, 98, 108, 101, 1, 0, 9, 83, 105, 103, 110, 97, 116,
      117, 114, 101, 1, 0, 27, 82, 117, 110, 116, 105, 109, 101, 73, 110, 118, 105, 115, 105, 98, 108, 101, 65, 110, 110, 111, 116, 97, 116,
      105, 111, 110, 115, 1, 0, 36, 82, 117, 110, 116, 105, 109, 101, 73, 110, 118, 105, 115, 105, 98, 108, 101, 80, 97, 114, 97, 109, 101,
      116, 101, 114, 65, 110, 110, 111, 116, 97, 116, 105, 111, 110, 115, 1, 0, 10, 83, 111, 117, 114, 99, 101, 70, 105, 108, 101, 1, 0, 25,
      82, 117, 110, 116, 105, 109, 101, 86, 105, 115, 105, 98, 108, 101, 65, 110, 110, 111, 116, 97, 116, 105, 111, 110, 115, 0, 49, 0, 2,
      0, 4, 0, 0, 0, 0, 0, 3, 0, 25, 0, 5, 0, 6, 0, 4, 0, 55, 0, 0, 0, 56, 0, 2, 0, 1, 0, 0, 0, 14, 42, 18, 10, -72, 0, 16, -78, 0, 22, 42,
      -74, 0, 25, -80, 0, 0, 0, 2, 0, 56, 0, 0, 0, 6, 0, 1, 0, 6, 0, 10, 0, 57, 0, 0, 0, 12, 0, 1, 0, 0, 0, 14, 0, 9, 0, 26, 0, 0, 0, 58, 0,
      0, 0, 2, 0, 7, 0, 59, 0, 0, 0, 6, 0, 1, 0, 8, 0, 0, 0, 60, 0, 0, 0, 7, 1, 0, 1, 0, 8, 0, 0, 0, 25, 0, 27, 0, 28, 0, 3, 0, 55, 0, 0, 0,
      56, 0, 2, 0, 1, 0, 0, 0, 14, 42, 18, 31, -72, 0, 16, -78, 0, 22, 42, -74, 0, 34, -79, 0, 0, 0, 2, 0, 56, 0, 0, 0, 6, 0, 1, 0, 6, 0,
      12, 0, 57, 0, 0, 0, 12, 0, 1, 0, 0, 0, 14, 0, 30, 0, 26, 0, 0, 0, 58, 0, 0, 0, 2, 0, 29, 0, 60, 0, 0, 0, 7, 1, 0, 1, 0, 8, 0, 0, 0,
      25, 0, 35, 0, 28, 0, 3, 0, 55, 0, 0, 0, 56, 0, 2, 0, 1, 0, 0, 0, 14, 42, 18, 31, -72, 0, 16, -78, 0, 22, 42, -74, 0, 38, -79, 0, 0, 0,
      2, 0, 56, 0, 0, 0, 6, 0, 1, 0, 6, 0, 14, 0, 57, 0, 0, 0, 12, 0, 1, 0, 0, 0, 14, 0, 30, 0, 26, 0, 0, 0, 58, 0, 0, 0, 2, 0, 29, 0, 60,
      0, 0, 0, 7, 1, 0, 1, 0, 8, 0, 0, 0, 2, 0, 61, 0, 0, 0, 2, 0, 54, 0, 62, 0, 0, 0, 70, 0, 1, 0, 39, 0, 5, 0, 40, 91, 0, 3, 73, 0, 41,
      73, 0, 42, 73, 0, 43, 0, 44, 73, 0, 45, 0, 46, 73, 0, 47, 0, 48, 91, 0, 1, 115, 0, 49, 0, 50, 91, 0, 9, 115, 0, 5, 115, 0, 26, 115, 0,
      51, 115, 0, 9, 115, 0, 27, 115, 0, 52, 115, 0, 30, 115, 0, 35, 115, 0, 53};
}
