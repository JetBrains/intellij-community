// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.openapi.diagnostic.LoggerRt;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.UrlUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.function.Predicate;

/**
 * A class loader that allows for various customizations, e.g. not locking jars or using a special cache to speed up class loading.
 * Should be constructed using {@link #build()} method.
 */
public class UrlClassLoader extends ClassLoader {
  protected static final boolean USE_PARALLEL_LOADING = Boolean.parseBoolean(System.getProperty("use.parallel.class.loading", "true"));
  private static final boolean isParallelCapable = USE_PARALLEL_LOADING && registerAsParallelCapable();

  static final String CLASS_EXTENSION = ".class";
  private static final ThreadLocal<Boolean> ourSkipFindingResource = new ThreadLocal<>();

  private final List<Path> files;
  private final ClassPath classPath;
  private final ClassLoadingLocks classLoadingLocks;
  private final boolean isBootstrapResourcesAllowed;

  /**
   * Called by the VM to support dynamic additions to the class path.
   *
   * @see java.lang.instrument.Instrumentation#appendToSystemClassLoaderSearch
   */
  @SuppressWarnings("unused")
  final void appendToClassPathForInstrumentation(@NotNull String jar) {
    Path file = Paths.get(jar);
    //noinspection deprecation
    classPath.addURL(file);
    files.add(file);
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
  public final long @NotNull [] getLoadingStats() {
    return new long[]{ClassPath.getTotalTime(), ClassPath.getTotalRequests()};
  }

  public static @NotNull UrlClassLoader.Builder build() {
    return new Builder();
  }

  /** @deprecated use {@link #build()} (left for compatibility with `java.system.class.loader` setting) */
  @Deprecated
  @ReviseWhenPortedToJDK("9")
  public UrlClassLoader(@NotNull ClassLoader parent) {
    this(createDefaultBuilderForJdk(parent), null, isParallelCapable);

    // without this ToolProvider.getSystemJavaCompiler() does not work in jdk 9+
    try {
      Field f = ClassLoader.class.getDeclaredField("classLoaderValueMap");
      f.setAccessible(true);
      f.set(this, f.get(parent));
    }
    catch (Exception ignored) {
    }
  }

  private static @NotNull UrlClassLoader.Builder createDefaultBuilderForJdk(@NotNull ClassLoader parent) {
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

  protected UrlClassLoader(@NotNull UrlClassLoader.Builder builder,
                           @Nullable ClassPath.ResourceFileFactory resourceFileFactory,
                           boolean isParallelCapable) {
    super(builder.parent);

    files = builder.files;

    Set<Path> urlsWithProtectionDomain = builder.pathsWithProtectionDomain;
    if (urlsWithProtectionDomain == null) {
      urlsWithProtectionDomain = Collections.emptySet();
    }

    classPath = new ClassPath(files, urlsWithProtectionDomain, builder, resourceFileFactory);

    isBootstrapResourcesAllowed = builder.isBootstrapResourcesAllowed;
    classLoadingLocks = isParallelCapable ? new ClassLoadingLocks() : null;
  }

  /** @deprecated adding URLs to a classloader at runtime could lead to hard-to-debug errors */
  @Deprecated
  public final void addURL(@NotNull URL url) {
    Path file = Paths.get(url.getPath());
    classPath.addURL(file);
    files.add(file);
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
    Class<?> clazz = _findClass(name);
    if (clazz == null) {
      throw new ClassNotFoundException(name);
    }
    return clazz;
  }

  protected final @Nullable Class<?> _findClass(@NotNull String name) {
    Resource resource = classPath.getResource(name.replace('.', '/') + CLASS_EXTENSION);
    if (resource == null) {
      return null;
    }

    try {
      return defineClass(name, resource);
    }
    catch (IOException e) {
      LoggerRt.getInstance(UrlClassLoader.class).error(e);
      return null;
    }
  }

  private Class<?> defineClass(@NotNull String name, @NotNull Resource resource) throws IOException {
    int i = name.lastIndexOf('.');
    if (i != -1) {
      String packageName = name.substring(0, i);
      // Check if package already loaded.
      Package aPackage = getPackage(packageName);
      if (aPackage == null) {
        try {
          Map<Resource.Attribute, String> attributes = resource.getAttributes();
          definePackage(packageName,
                        attributes == null ? null : attributes.get(Resource.Attribute.SPEC_TITLE),
                        attributes == null ? null : attributes.get(Resource.Attribute.SPEC_VERSION),
                        attributes == null ? null : attributes.get(Resource.Attribute.SPEC_VENDOR),
                        attributes == null ? null : attributes.get(Resource.Attribute.IMPL_TITLE),
                        attributes == null ? null : attributes.get(Resource.Attribute.IMPL_VERSION),
                        attributes == null ? null : attributes.get(Resource.Attribute.IMPL_VENDOR),
                        null);
        }
        catch (IllegalArgumentException ignore) {
          // do nothing, package already defined by some another thread
        }
      }
    }

    ProtectionDomain protectionDomain = resource.getProtectionDomain();
    if (protectionDomain == null) {
      protectionDomain = getProtectionDomain();
    }
    return _defineClass(name, resource, protectionDomain);
  }

  protected ProtectionDomain getProtectionDomain() {
    return null;
  }

  protected Class<?> _defineClass(String name, Resource resource, @Nullable ProtectionDomain protectionDomain) throws IOException {
    byte[] data = resource.getBytes();
    return defineClass(name, data, 0, data.length, protectionDomain);
  }

  @Override
  public URL findResource(String name) {
    if (ourSkipFindingResource.get() != null) {
      return null;
    }
    Resource resource = findResourceImpl(name);
    return resource == null ? null : resource.getURL();
  }

  private @Nullable Resource findResourceImpl(@NotNull String name) {
    String n = toCanonicalPath(name);
    Resource resource = classPath.getResource(n);
    // compatibility with existing code, non-standard classloader behavior
    if (resource == null && n.startsWith("/")) {
      return classPath.getResource(n.substring(1));
    }
    return resource;
  }

  @Override
  public @Nullable InputStream getResourceAsStream(String name) {
    if (isBootstrapResourcesAllowed) {
      ourSkipFindingResource.set(Boolean.TRUE);
      try {
        InputStream stream = super.getResourceAsStream(name);
        if (stream != null) {
          return stream;
        }
      }
      finally {
        ourSkipFindingResource.set(null);
      }
    }

    try {
      Resource resource = findResourceImpl(name);
      return resource == null ? null : resource.getInputStream();
    }
    catch (IOException e) {
      return null;
    }
  }

  @Override
  protected Enumeration<URL> findResources(String name) throws IOException {
    return classPath.getResources(name);
  }

  @Override
  protected final @NotNull Object getClassLoadingLock(String className) {
    return classLoadingLocks == null ? this : classLoadingLocks.getOrCreateLock(className);
  }

  public @Nullable Class<?> loadClassInsideSelf(@NotNull String name, boolean forceLoadFromSubPluginClassloader) {
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
      return _findClass(name);
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
  private static String toCanonicalPath(@NotNull String path) {
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
    @Nullable Predicate<Path> cachingCondition;

    Builder() { }

    /**
     * @deprecated Use {@link #files(List)}. Using of {@link URL} is discoruaged in favoir of modern {@lin Path}.
     */
    @Deprecated
    public @NotNull UrlClassLoader.Builder urls(@NotNull List<URL> urls) {
      List<Path> files = new ArrayList<>(urls.size());
      for (URL url : urls) {
        files.add(Paths.get(url.getPath()));
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
    public @NotNull UrlClassLoader.Builder useCache(@NotNull UrlClassLoader.CachePool pool, @NotNull Predicate<Path> condition) {
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