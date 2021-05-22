// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.UrlUtilRt;
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
import java.security.ProtectionDomain;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * A class loader that allows for various customizations, e.g. not locking jars or using a special cache to speed up class loading.
 * Should be constructed using {@link #build()} method.
 */
public class UrlClassLoader extends ClassLoader implements ClassPath.ClassDataConsumer {
  protected static final boolean USE_PARALLEL_LOADING = Boolean.parseBoolean(System.getProperty("use.parallel.class.loading", "true"));
  private static final boolean isParallelCapable = USE_PARALLEL_LOADING && registerAsParallelCapable();

  private static final ThreadLocal<Boolean> skipFindingResource = new ThreadLocal<>();

  private final List<Path> files;
  protected final ClassPath classPath;
  private final ClassLoadingLocks classLoadingLocks;
  private final boolean isBootstrapResourcesAllowed;

  /**
   * Called by the VM to support dynamic additions to the class path.
   *
   * @see java.lang.instrument.Instrumentation#appendToSystemClassLoaderSearch
   */
  @SuppressWarnings("unused")
  final void appendToClassPathForInstrumentation(@NotNull String jar) {
    addFiles(Collections.singletonList(Paths.get(jar)));
  }

  /**
   * There are two definitions of ClassPath class.
   * First one from app class loader that used by bootstrap.
   * Another one from core class loader that created as result of creating of plugin class loader.
   * Core class loader doesn't use bootstrap class loader as parent, instead, only platform classloader is used (only JDK classes).
   */
  @ApiStatus.Internal
  public final @NotNull ClassPath getClassPath() {
    return classPath;
  }

  @ApiStatus.Internal
  public static @NotNull Collection<Map.Entry<String, Path>> getLoadedClasses() {
    return ClassPath.getLoadedClasses();
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
  @ReviseWhenPortedToJDK("9")
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
      configuration.files = new ArrayList<>(urls.length);
      for (URL url : urls) {
        configuration.files.add(Paths.get(url.getPath()));
      }
    }
    else {
      String[] parts = System.getProperty("java.class.path").split(System.getProperty("path.separator"));
      configuration.files = new ArrayList<>(parts.length);
      for (String s : parts) {
        configuration.files.add(new File(s).toPath());
      }
    }

    configuration.parent = parent.getParent();
    configuration.lockJars = true;
    configuration.useCache = true;
    configuration.isClassPathIndexEnabled = true;
    configuration.isBootstrapResourcesAllowed = Boolean.parseBoolean(System.getProperty("idea.allow.bootstrap.resources", "true"));
    configuration.autoAssignUrlsWithProtectionDomain();
    return configuration;
  }

  protected UrlClassLoader(@NotNull UrlClassLoader.Builder builder, boolean isParallelCapable) {
    this(builder, null, isParallelCapable);
  }

  /**
   * @deprecated Do not extend UrlClassLoader. If you cannot avoid it, use {@link #UrlClassLoader(Builder, boolean)}.
   */
  @Deprecated
  protected UrlClassLoader(@NotNull UrlClassLoader.Builder builder) {
    this(builder, null, false);
  }

  protected UrlClassLoader(@NotNull UrlClassLoader.Builder builder,
                           @Nullable ClassPath.ResourceFileFactory resourceFileFactory,
                           boolean isParallelCapable) {
    super(builder.parent);

    files = builder.files;

    Set<Path> urlsWithProtectionDomain = builder.pathsWithProtectionDomain;
    if (urlsWithProtectionDomain == null) {
      urlsWithProtectionDomain = Collections.emptySet();
    }

    classPath = new ClassPath(files, urlsWithProtectionDomain, builder, resourceFileFactory, this);

    isBootstrapResourcesAllowed = builder.isBootstrapResourcesAllowed;
    classLoadingLocks = isParallelCapable ? new ClassLoadingLocks() : null;
  }

  /** @deprecated adding URLs to a classloader at runtime could lead to hard-to-debug errors */
  @Deprecated
  public final void addURL(@NotNull URL url) {
    addFiles(Collections.singletonList(Paths.get(url.getPath())));
  }

  @ApiStatus.Internal
  public final void addFiles(@NotNull List<Path> files) {
    classPath.addFiles(files);
    this.files.addAll(files);
  }

  public final @NotNull List<URL> getUrls() {
    List<URL> result = new ArrayList<>();
    for (Path file : files) {
      try {
        result.add(file.toUri().toURL());
      }
      catch (MalformedURLException ignored) {
      }
    }
    return result;
  }

  public final @NotNull List<Path> getFiles() {
    return Collections.unmodifiableList(files);
  }

  public final boolean hasLoadedClass(String name) {
    Class<?> aClass = findLoadedClass(name);
    return aClass != null && aClass.getClassLoader() == this;
  }

  @Override
  protected Class<?> findClass(@NotNull String name) throws ClassNotFoundException {
    Class<?> clazz;
    try {
      clazz = classPath.findClass(name);
    }
    catch (IOException e) {
      throw new ClassNotFoundException(name, e);
    }
    if (clazz == null) {
      throw new ClassNotFoundException(name);
    }
    return clazz;
  }

  private void definePackageIfNeeded(@NotNull String name, Loader loader) throws IOException {
    int lastDotIndex = name.lastIndexOf('.');
    if (lastDotIndex == -1) {
      return;
    }

    String packageName = name.substring(0, lastDotIndex);
    // check if package already loaded
    if (isPackageDefined(packageName)) {
      return;
    }

    try {
      Map<Loader.Attribute, String> attributes = loader.getAttributes();
      if (attributes == null || attributes.isEmpty()) {
        definePackage(packageName, null, null, null, null, null, null, null);
      }
      else {
        definePackage(packageName,
                      attributes.get(Loader.Attribute.SPEC_TITLE),
                      attributes.get(Loader.Attribute.SPEC_VERSION),
                      attributes.get(Loader.Attribute.SPEC_VENDOR),
                      attributes.get(Loader.Attribute.IMPL_TITLE),
                      attributes.get(Loader.Attribute.IMPL_VERSION),
                      attributes.get(Loader.Attribute.IMPL_VENDOR),
                      null);
      }
    }
    catch (IllegalArgumentException ignore) {
      // do nothing, package already defined by some another thread
    }
  }

  protected boolean isPackageDefined(String packageName) {
    //noinspection deprecation
    return getPackage(packageName) != null;
  }

  protected ProtectionDomain getProtectionDomain() {
    return null;
  }

  @Override
  public boolean isByteBufferSupported(@NotNull String name, @Nullable ProtectionDomain protectionDomain) {
    return true;
  }

  @Override
  public Class<?> consumeClassData(@NotNull String name, byte[] data, Loader loader, @Nullable ProtectionDomain protectionDomain)
    throws IOException {
    definePackageIfNeeded(name, loader);
    return super.defineClass(name, data, 0, data.length, protectionDomain == null ? getProtectionDomain() : protectionDomain);
  }

  @Override
  public Class<?> consumeClassData(@NotNull String name, ByteBuffer data, Loader loader, @Nullable ProtectionDomain protectionDomain)
    throws IOException {
    definePackageIfNeeded(name, loader);
    return super.defineClass(name, data, protectionDomain == null ? getProtectionDomain() : protectionDomain);
  }

  @Override
  public @Nullable URL findResource(@NotNull String name) {
    if (skipFindingResource.get() != null) {
      return null;
    }
    Resource resource = doFindResource(name);
    return resource != null ? resource.getURL() : null;
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
        skipFindingResource.set(null);
      }
    }

    return null;
  }

  private @Nullable Resource doFindResource(@NotNull String name) {
    String canonicalPath = toCanonicalPath(name);
    Resource resource = classPath.findResource(canonicalPath);
    if (resource == null && canonicalPath.startsWith("/")) {
      //noinspection SpellCheckingInspection
      if (!canonicalPath.startsWith("/org/bridj/")) {
        logError("Do not request resource from classloader using path with leading slash", new IllegalArgumentException(name));
      }
      resource = classPath.findResource(canonicalPath.substring(1));
    }
    return resource;
  }

  public final void processResources(@NotNull String dir,
                                     @NotNull Predicate<? super String> fileNameFilter,
                                     @NotNull BiConsumer<? super String, ? super InputStream> consumer) throws IOException {
    classPath.processResources(dir, fileNameFilter, consumer);
  }

  @Override
  protected @NotNull Enumeration<URL> findResources(@NotNull String name) throws IOException {
    return classPath.getResources(name);
  }

  @Override
  protected final @NotNull Object getClassLoadingLock(String className) {
    return classLoadingLocks == null ? this : classLoadingLocks.getOrCreateLock(className);
  }

  public @Nullable Class<?> loadClassInsideSelf(@NotNull String name, boolean forceLoadFromSubPluginClassloader) throws IOException {
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
          catch (ClassNotFoundException ignore) {
          }
        }

        if (c != null) {
          return c;
        }
      }
      return classPath.findClass(name);
    }
  }

  /**
   * An interface for a pool to store internal caches that can be shared between different class loaders,
   * if they contain the same URLs in their class paths.<p/>
   *
   * The implementation is subject to change so one shouldn't rely on it.
   *
   * @see #createCachePool()
   * @see Builder#useCache
   */
  public interface CachePool { }

  /**
   * @return a new pool to be able to share internal caches between different class loaders, if they contain the same URLs
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

    // trying to speedup the common case when there are no "//" or "/."
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

  @SuppressWarnings("DuplicatedCode")
  private static void processDots(@NotNull StringBuilder result, int dots, int start) {
    if (dots == 2) {
      int pos = -1;
      if (!StringUtilRt.endsWith(result, "/../") && !"../".contentEquals(result)) {
        pos = StringUtilRt.lastIndexOf(result, '/', start, result.length() - 1);
        if (pos >= 0) {
          ++pos;  // separator found, trim to next char
        }
        else if (start > 0) {
          pos = start;  // path is absolute, trim to root ('/..' -> '/')
        }
        else if (result.length() > 0) {
          pos = 0;  // path is relative, trim to default ('a/..' -> '')
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
  private static int processRoot(@NotNull String path, @NotNull StringBuilder result) {
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
      Class<?> logger = Class.forName("com.intellij.openapi.diagnostic.Logger", false, this);
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      Object instance = lookup.findStatic(logger, "getInstance", MethodType.methodType(logger, Class.class)).invoke(getClass());
      lookup.findVirtual(logger, "error", MethodType.methodType(void.class, String.class, Throwable.class))
        .bindTo(instance)
        .invokeExact(message, t);
    }
    catch (Throwable tt) {
      tt.addSuppressed(t);
      tt.printStackTrace(System.err);
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
    private static final boolean isClassPathIndexEnabledGlobalValue = Boolean.parseBoolean(System.getProperty("idea.classpath.index.enabled", "true"));

    List<Path> files = Collections.emptyList();
    Set<Path> pathsWithProtectionDomain;
    ClassLoader parent;
    boolean lockJars = true;
    boolean useCache;
    boolean isClassPathIndexEnabled;
    boolean preloadJarContents = true;
    boolean isBootstrapResourcesAllowed;
    boolean errorOnMissingJar = true;
    @Nullable CachePoolImpl cachePool;
    Predicate<? super Path> cachingCondition;

    Builder() { }

    /**
     * @deprecated Use {@link #files(List)}. Using of {@link URL} is discouraged in favor of modern {@link Path}.
     */
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

    /**
     * Marks URLs that are signed by Sun/Oracle and whose signatures must be verified.
     */
    @NotNull UrlClassLoader.Builder urlsWithProtectionDomain(@NotNull Set<Path> value) {
      pathsWithProtectionDomain = value;
      return this;
    }

    public @NotNull UrlClassLoader.Builder parent(ClassLoader parent) {
      this.parent = parent;
      return this;
    }

    /**
     * ZipFile handles opened in JarLoader will be kept in SoftReference. Depending on OS, the option significantly speeds up classloading
     * from libraries. Caveat: for Windows opened handle will lock the file preventing its modification.
     * Thus, the option is recommended when jars are not modified or process that uses this option is transient.
     */
    public @NotNull UrlClassLoader.Builder allowLock(boolean lockJars) {
      this.lockJars = lockJars;
      return this;
    }

    /**
     * Build backward index of packages / class or resource names that allows avoiding IO during classloading.
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
     * FileLoader will save list of files / packages under its root and use this information instead of walking filesystem for
     * speedier classloading. Should be used only when the caches could be properly invalidated, e.g. when new file appears under
     * FileLoader's root. Currently, the flag is used for faster unit test / developed Idea running, because Idea's make (as of 14.1) ensures deletion of
     * such information upon appearing new file for output root.
     * N.b. Idea make does not ensure deletion of cached information upon deletion of some file under local root but false positives are not a
     * logical error since code is prepared for that and disk access is performed upon class / resource loading.
     * See also Builder#usePersistentClasspathIndexForLocalClassDirectories.
     */
    public @NotNull UrlClassLoader.Builder usePersistentClasspathIndexForLocalClassDirectories() {
      this.isClassPathIndexEnabled = isClassPathIndexEnabledGlobalValue;
      return this;
    }

    /**
     * Requests the class loader being built to use cache and, if possible, retrieve and store the cached data from a special cache pool
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
      preloadJarContents = false;
      return this;
    }

    public @NotNull UrlClassLoader.Builder allowBootstrapResources() {
      return allowBootstrapResources(true);
    }

    public @NotNull UrlClassLoader.Builder allowBootstrapResources(boolean allowBootstrapResources) {
      isBootstrapResourcesAllowed = allowBootstrapResources;
      return this;
    }

    public @NotNull UrlClassLoader.Builder setLogErrorOnMissingJar(boolean log) {
      errorOnMissingJar = log;
      return this;
    }

    public @NotNull UrlClassLoader.Builder autoAssignUrlsWithProtectionDomain() {
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
      return new UrlClassLoader(this, null, isParallelCapable);
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
}
