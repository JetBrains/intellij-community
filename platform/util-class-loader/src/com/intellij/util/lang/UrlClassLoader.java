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

  @ApiStatus.Internal
  protected final ClassPath classPath;
  private final ClassLoadingLocks classLoadingLocks;
  private final boolean isBootstrapResourcesAllowed;
  private final boolean isSystemClassLoader;

  @ApiStatus.Internal
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
   * There are two definitions of the `ClassPath` class: one from the app class loader that bootstrap uses,
   * and another one from the core class loader produced as a result of creating a plugin class loader.
   * The core class loader doesn't use bootstrap class loader as a parent - instead, only platform classloader is used (only JRE classes).
   */
  @ApiStatus.Internal
  public final @NotNull ClassPath getClassPath() {
    return classPath;
  }

  /**
   * @see com.intellij.TestAll#getClassRoots()
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

    classPath = new ClassPath(builder.files, builder, resourceFileFactory, mimicJarUrlConnection);

    isBootstrapResourcesAllowed = builder.isBootstrapResourcesAllowed;
    classLoadingLocks = isParallelCapable ? new ClassLoadingLocks() : null;
  }

  @ApiStatus.Internal
  protected UrlClassLoader(@NotNull ClassPath classPath) {
    super(null);

    this.classPath = classPath;
    isBootstrapResourcesAllowed = false;
    isSystemClassLoader = false;
    classLoadingLocks = new ClassLoadingLocks();
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
    // com.intellij.util.lang
    // see XxHash3Test.packages
    if (isSystemClassLoader && packageNameHash == -9217824570049207139L && isNotExcludedLangClasses(fileNameWithoutExtension)) {
      return appClassLoader.loadClass(name);
    }

    Class<?> aClass;
    try {
      aClass = classPath.findClass(name, fileName, packageNameHash, classDataConsumer);
    }
    catch (IOException e) {
      throw new ClassNotFoundException(name, e);
    }
    if (aClass == null) {
      throw new ClassNotFoundException(name);
    }
    return aClass;
  }

  @ApiStatus.Internal
  public @Nullable Class<?> loadClassWithPrecomputedMeta(String name,
                                                         String fileName,
                                                         String fileNameWithoutExtension,
                                                         long packageNameHash) throws IOException, ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
      Class<?> c = findLoadedClass(name);
      if (c != null) {
        return c;
      }

      ClassLoader parent = getParent();
      if (parent != null) {
        try {
          c = parent.loadClass(name);
        }
        catch (ClassNotFoundException ignored) {
        }
      }

      if (c != null) {
        return c;
      }
      if (isSystemClassLoader && packageNameHash == -9217824570049207139L && isNotExcludedLangClasses(fileNameWithoutExtension)) {
        return appClassLoader.loadClass(name);
      }
      return classPath.findClass(name, fileName, packageNameHash, classDataConsumer);
    }
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

  private static boolean isNotExcludedLangClasses(String fileNameWithoutExtension) {
    // these two classes from com.intellij.util.lang are located in intellij.platform.util module,
    // which shouldn't be loaded by appClassLoader (IDEA-331043)
    return !fileNameWithoutExtension.endsWith("/CompoundRuntimeException") && !fileNameWithoutExtension.endsWith("/JavaVersion");
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
  @ApiStatus.Internal
  public interface CachePool { }

  /**
   * @return a new pool to be able to share internal caches between different class loaders if they contain the same URLs
   * in their class paths.
   */
  @ApiStatus.Internal
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
     *  Warning: on Windows, an unclosed handle locks a file, preventing its modification.
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
     * such information upon appearing a new file for output root.
     * <p>
     * IDEA's building process does not ensure deletion of cached information upon deletion of some file under a local root.
     * However, false positives are not a logical error,
     * since code is prepared for that and disk access is performed upon class/resource loading.
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
    @ApiStatus.Internal
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
}
