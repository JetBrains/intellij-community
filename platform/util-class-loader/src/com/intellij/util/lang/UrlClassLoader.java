// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.openapi.diagnostic.LoggerRt;
import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.*;

/**
 * A class loader that allows for various customizations, e.g. not locking jars or using a special cache to speed up class loading.
 * Should be constructed using {@link #build()} method.
 */
public class UrlClassLoader extends ClassLoader {
  static final String CLASS_EXTENSION = ".class";
  private static final ThreadLocal<Boolean> ourSkipFindingResource = new ThreadLocal<>();

  private static final Set<Class<?>> ourParallelCapableLoaders;

  private final List<Path> files;
  private final ClassPath classPath;
  private final ClassLoadingLocks classLoadingLocks;
  private final boolean isBootstrapResourcesAllowed;

  static {
    boolean ibmJvm = System.getProperty("java.vm.vendor", "unknown").toLowerCase(Locale.ENGLISH).contains("ibm");
    boolean capable = !ibmJvm && Boolean.parseBoolean(System.getProperty("use.parallel.class.loading", "true"));
    if (capable) {
      ourParallelCapableLoaders = Collections.synchronizedSet(new HashSet<>());
      try {
        if (registerAsParallelCapable()) {
          ourParallelCapableLoaders.add(UrlClassLoader.class);
        }
      }
      catch (Exception ignored) {
      }
    }
    else {
      ourParallelCapableLoaders = null;
    }
  }

  protected static void markParallelCapable(@NotNull Class<? extends UrlClassLoader> loaderClass) {
    assert ourParallelCapableLoaders != null;
    ourParallelCapableLoaders.add(loaderClass);
  }

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

  public static @NotNull PathClassLoaderBuilder build() {
    return new PathClassLoaderBuilder();
  }

  /** @deprecated use {@link #build()} (left for compatibility with `java.system.class.loader` setting) */
  @Deprecated
  @ReviseWhenPortedToJDK("9")
  public UrlClassLoader(@NotNull ClassLoader parent) {
    this(build().urlsFromAppClassLoader(parent).parent(parent.getParent()).allowLock().useCache()
           .usePersistentClasspathIndexForLocalClassDirectories()
           .allowBootstrapResources(Boolean.parseBoolean(System.getProperty("idea.allow.bootstrap.resources", "true")))
           .useLazyClassloadingCaches(Boolean.parseBoolean(System.getProperty("idea.lazy.classloading.caches", "false")))
           .autoAssignUrlsWithProtectionDomain());

    // without this ToolProvider.getSystemJavaCompiler() does not work in jdk 9+
    try {
      Field f = ClassLoader.class.getDeclaredField("classLoaderValueMap");
      f.setAccessible(true);
      f.set(this, f.get(parent));
    }
    catch (Exception ignored) {
    }
  }

  protected UrlClassLoader(@NotNull PathClassLoaderBuilder builder) {
    super(builder.myParent);

    files = builder.files;

    Set<Path> urlsWithProtectionDomain = builder.pathsWithProtectionDomain;
    if (urlsWithProtectionDomain == null) {
      urlsWithProtectionDomain = Collections.emptySet();
    }

    classPath = new ClassPath(files, urlsWithProtectionDomain, builder);

    isBootstrapResourcesAllowed = builder.myAllowBootstrapResources;
    classLoadingLocks = ourParallelCapableLoaders != null && ourParallelCapableLoaders.contains(getClass()) ? new ClassLoadingLocks() : null;
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
      String pkgName = name.substring(0, i);
      // Check if package already loaded.
      Package pkg = getPackage(pkgName);
      if (pkg == null) {
        try {
          definePackage(pkgName,
                        resource.getValue(Resource.Attribute.SPEC_TITLE),
                        resource.getValue(Resource.Attribute.SPEC_VERSION),
                        resource.getValue(Resource.Attribute.SPEC_VENDOR),
                        resource.getValue(Resource.Attribute.IMPL_TITLE),
                        resource.getValue(Resource.Attribute.IMPL_VERSION),
                        resource.getValue(Resource.Attribute.IMPL_VENDOR),
                        null);
        }
        catch (IllegalArgumentException ignore) {
          // do nothing, package already defined by some another thread
        }
      }
    }

    byte[] content = resource.getBytes();
    ProtectionDomain protectionDomain = resource.getProtectionDomain();
    if (protectionDomain == null) {
      protectionDomain = getProtectionDomain();
      if (protectionDomain == null) {
        return _defineClass(name, content);
      }
    }
    return _defineClass(name, content, protectionDomain);
  }

  protected ProtectionDomain getProtectionDomain() {
    return null;
  }

  protected Class<?> _defineClass(final String name, final byte[] b) {
    return defineClass(name, b, 0, b.length);
  }

  protected Class<?> _defineClass(final String name, final byte[] b, @Nullable ProtectionDomain protectionDomain) {
    return defineClass(name, b, 0, b.length, protectionDomain);
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
      resource = classPath.getResource(n.substring(1));
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
   * @see PathClassLoaderBuilder#useCache
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
}