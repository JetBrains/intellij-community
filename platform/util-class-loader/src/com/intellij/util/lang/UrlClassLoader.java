// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.openapi.diagnostic.LoggerRt;
import com.intellij.openapi.util.io.FileUtilRt;
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
import java.nio.file.Path;
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
    try {
      URL url = new File(jar).toURI().toURL();
      //noinspection deprecation
      classPath.addURL(UrlUtilRt.internProtocol(url));
      urls.add(url);
    }
    catch (MalformedURLException ignore) { }
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

  private final List<URL> urls;
  private final ClassPath classPath;
  private final ClassLoadingLocks classLoadingLocks;
  private final boolean isBootstrapResourcesAllowed;

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

    if (builder.urls.isEmpty() || builder.myUrlsInterned) {
      List<Path> paths = builder.paths;
      if (paths.isEmpty()) {
        urls = builder.urls;
      }
      else {
        urls = new ArrayList<>(paths.size());
        for (Path path : paths) {
          try {
            urls.add(path.normalize().toUri().toURL());
          }
          catch (MalformedURLException e) {
            throw new RuntimeException(e);
          }
        }
      }
    }
    else {
      urls = new ArrayList<>(builder.urls.size());
      for (URL url : builder.urls) {
        URL internedUrl = UrlUtilRt.internProtocol(url);
        if (internedUrl != null) {
          urls.add(internedUrl);
        }
      }
    }

    Set<URL> urlsWithProtectionDomain = builder.urlsWithProtectionDomain;
    if (urlsWithProtectionDomain == null) {
      urlsWithProtectionDomain = Collections.emptySet();
    }

    classPath = new ClassPath(urls, urlsWithProtectionDomain, builder);

    isBootstrapResourcesAllowed = builder.myAllowBootstrapResources;
    classLoadingLocks = ourParallelCapableLoaders != null && ourParallelCapableLoaders.contains(getClass()) ? new ClassLoadingLocks() : null;
  }

  /** @deprecated adding URLs to a classloader at runtime could lead to hard-to-debug errors */
  @Deprecated
  public final void addURL(@NotNull URL url) {
    classPath.addURL(UrlUtilRt.internProtocol(url));
    urls.add(url);
  }

  public final @NotNull List<URL> getUrls() {
    return Collections.unmodifiableList(urls);
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

  private @Nullable Resource findResourceImpl(String name) {
    String n = FileUtilRt.toCanonicalPath(name, '/', false);
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
}