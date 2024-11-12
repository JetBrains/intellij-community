// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang.java6;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.openapi.diagnostic.LoggerRt;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.PathUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.util.*;

/**
 * A class loader that allows for various customizations, e.g. not locking jars or using a special cache to speed up class loading.
 * Should be constructed using {@link #build()} method.
 */
public final class UrlClassLoader extends ClassLoader {
  static final String CLASS_EXTENSION = ".class";
  private static final ThreadLocal<Boolean> ourSkipFindingResource = new ThreadLocal<>();
  private static final boolean ourClassPathIndexEnabled = Boolean.parseBoolean(System.getProperty("idea.classpath.index.enabled", "false"));

  private static final Set<Class<?>> ourParallelCapableLoaders;
  static {
    boolean ibmJvm = System.getProperty("java.vm.vendor", "unknown").toLowerCase(Locale.ENGLISH).contains("ibm");
    boolean capable = !ibmJvm;
    if (capable) {
      ourParallelCapableLoaders = Collections.synchronizedSet(new HashSet<Class<?>>());
      try {
        //todo Patches.USE_REFLECTION_TO_ACCESS_JDK7
        Method registerAsParallelCapable = ClassLoader.class.getDeclaredMethod("registerAsParallelCapable");
        registerAsParallelCapable.setAccessible(true);
        if (Boolean.TRUE.equals(registerAsParallelCapable.invoke(null))) {
          ourParallelCapableLoaders.add(UrlClassLoader.class);
        }
      }
      catch (Exception ignored) { }
    }
    else {
      ourParallelCapableLoaders = null;
    }
  }

  private static boolean isUrlNeedsProtectionDomain(@NotNull URL url) {
    String name = PathUtilRt.getFileName(url.getPath());
    //noinspection SpellCheckingInspection
    return name.endsWith(".jar") && (name.startsWith("bcprov-") || name.startsWith("bcpkix-"));  // BouncyCastle needs protection domain
  }

  /**
   * Called by the VM to support dynamic additions to the class path.
   *
   * @see java.lang.instrument.Instrumentation#appendToSystemClassLoaderSearch
   */
  @SuppressWarnings("unused")
  void appendToClassPathForInstrumentation(@NotNull String jar) {
    try {
      URL url = new File(jar).toURI().toURL();
      //noinspection deprecation
      getClassPath().addURL(url);
      myURLs.add(url);
    }
    catch (MalformedURLException ignore) { }
  }

  @NotNull
  private ClassPath getClassPath() {
    return myClassPath;
  }

  // called via reflection
  @SuppressWarnings({"unused", "MethodMayBeStatic"})
  @NotNull
  public long[] getLoadingStats() {
    return new long[]{ClassPath.getTotalTime(), ClassPath.getTotalRequests()};
  }

  public static final class Builder {
    private List<URL> myURLs = Collections.emptyList();
    private Set<URL> myURLsWithProtectionDomain;
    private ClassLoader myParent;
    private boolean myLockJars;
    private boolean myUseCache;
    private boolean myUsePersistentClasspathIndex = ourClassPathIndexEnabled;
    private boolean myAllowBootstrapResources;
    private boolean myLazyClassloadingCaches;

    Builder() { }

    @NotNull
    public Builder urls(@NotNull List<URL> urls) { myURLs = urls; return this; }
    @NotNull
    public Builder urls(@NotNull URL... urls) { myURLs = Arrays.asList(urls); return this; }

    // Presense of this method is also checked in JUnitDevKitPatcher
    private Builder urlsFromAppClassLoader(ClassLoader classLoader) {
      if (classLoader instanceof URLClassLoader) {
        return urls(((URLClassLoader)classLoader).getURLs());
      }
      String[] parts = System.getProperty("java.class.path").split(File.pathSeparator);
      myURLs = new ArrayList<>(parts.length);
      for (String s : parts) {
        try {
          myURLs.add(new File(s).toURI().toURL());
        } catch (IOException ignored) {
        }
      }
      return this;
    }

    @NotNull
    public Builder parent(ClassLoader parent) { myParent = parent; return this; }

    /**
     * ZipFile handles opened in JarLoader will be kept in SoftReference. Depending on OS, the option significantly speeds up classloading
     * from libraries. Caveat: for Windows opened handle will lock the file preventing its modification.
     * Thus, the option is recommended when jars are not modified or process that uses this option is transient.
     */
    @NotNull
    public Builder allowLock() { myLockJars = true; return this; }

    /**
     * Build backward index of packages / class or resource names that allows avoiding IO during classloading.
     */
    @NotNull
    public Builder useCache() { myUseCache = true; return this; }

    /**
     * FileLoader will save list of files / packages under its root and use this information instead of walking filesystem for
     * speedier classloading. Should be used only when the caches could be properly invalidated, e.g. when new file appears under
     * FileLoader's root. Currently, the flag is used for faster unit test / developed Idea running, because Idea's make (as of 14.1) ensures deletion of
     * such information upon appearing new file for output root.
     * N.b. Idea make does not ensure deletion of cached information upon deletion of some file under local root but false positives are not a
     * logical error since code is prepared for that and disk access is performed upon class / resource loading.
     * See also Builder#usePersistentClasspathIndexForLocalClassDirectories.
     */
    @NotNull
    public Builder usePersistentClasspathIndexForLocalClassDirectories() {
      myUsePersistentClasspathIndex = true;
      return this;
    }

    @NotNull
    public Builder allowBootstrapResources(boolean allowBootstrapResources) { myAllowBootstrapResources = allowBootstrapResources; return this; }

    /**
     * Package contents information in Jar/File loaders will be lazily retrieved / cached upon classloading.
     * Important: this option will result in much smaller initial overhead but for bulk classloading (like complete IDE start) it is less
     * efficient (in number of disk / native code accesses / CPU spent) than combination of useCache / usePersistentClasspathIndexForLocalClassDirectories.
     */
    @NotNull
    public Builder useLazyClassloadingCaches(boolean pleaseBeLazy) { myLazyClassloadingCaches = pleaseBeLazy; return this; }

    @NotNull
    public Builder autoAssignUrlsWithProtectionDomain() {
      Set<URL> result = new HashSet<>();
      for (URL url : myURLs) {
        if (isUrlNeedsProtectionDomain(url)) {
          result.add(url);
        }
      }
      myURLsWithProtectionDomain = result;
      return this;
    }

    @NotNull
    public UrlClassLoader get() {
      return new UrlClassLoader(this);
    }
  }

  @NotNull
  public static UrlClassLoader create(List<URL> urls) {
    return build().urls(urls).useCache().allowLock().get();
  }

  @NotNull
  public static Builder build() {
    return new Builder();
  }

  private final List<URL> myURLs;
  private final ClassPath myClassPath;
  private final ClassLoadingLocks myClassLoadingLocks;
  private final boolean myAllowBootstrapResources;

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

  private UrlClassLoader(@NotNull Builder builder) {
    super(builder.myParent);

    myURLs = builder.myURLs;

    myClassPath = createClassPath(builder);
    myAllowBootstrapResources = builder.myAllowBootstrapResources;
    myClassLoadingLocks = ourParallelCapableLoaders != null && ourParallelCapableLoaders.contains(getClass()) ? new ClassLoadingLocks() : null;
  }

  @NotNull
  private ClassPath createClassPath(@NotNull Builder builder) {
    Set<URL> urlsWithProtectionDomain = builder.myURLsWithProtectionDomain;
    if (urlsWithProtectionDomain == null) {
      urlsWithProtectionDomain = Collections.emptySet();
    }

    return new ClassPath(myURLs, builder.myLockJars, builder.myUseCache,
                         builder.myUsePersistentClasspathIndex, true, builder.myLazyClassloadingCaches, urlsWithProtectionDomain
    );
  }

  /** @deprecated adding URLs to a classloader at runtime could lead to hard-to-debug errors */
  @Deprecated
  public void addURL(@NotNull URL url) {
    getClassPath().addURL(url);
    myURLs.add(url);
  }

  @Override
  protected Class<?> findClass(@NotNull String name) throws ClassNotFoundException {
    Class<?> clazz = _findClass(name);
    if (clazz == null) {
      throw new ClassNotFoundException(name);
    }
    return clazz;
  }

  @Nullable
  private Class<?> _findClass(@NotNull String name) {
    Resource resource = getClassPath().getResource(name.replace('.', '/') + CLASS_EXTENSION);
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

  private Class<?> defineClass(@NotNull String name, @NotNull Resource res) throws IOException {
    int i = name.lastIndexOf('.');
    if (i != -1) {
      String pkgName = name.substring(0, i);
      // Check if package already loaded.
      Package pkg = getPackage(pkgName);
      if (pkg == null) {
        try {
          definePackage(pkgName,
                        res.getValue(Resource.Attribute.SPEC_TITLE),
                        res.getValue(Resource.  Attribute.SPEC_VERSION),
                        res.getValue(Resource.Attribute.SPEC_VENDOR),
                        res.getValue(Resource.Attribute.IMPL_TITLE),
                        res.getValue(Resource.Attribute.IMPL_VERSION),
                        res.getValue(Resource.Attribute.IMPL_VENDOR),
                        null);
        }
        catch (IllegalArgumentException e) {
          // do nothing, package already defined by some another thread
        }
      }
    }

    byte[] b = res.getBytes();
    ProtectionDomain protectionDomain = res.getProtectionDomain();
    if (protectionDomain != null) {
      return _defineClass(name, b, protectionDomain);
    }
    return _defineClass(name, b);
  }

  private Class<?> _defineClass(final String name, final byte[] b) {
    return defineClass(name, b, 0, b.length);
  }

  private Class<?> _defineClass(final String name, final byte[] b, @Nullable ProtectionDomain protectionDomain) {
    return defineClass(name, b, 0, b.length, protectionDomain);
  }

  @Override
  public URL findResource(String name) {
    if (ourSkipFindingResource.get() != null) {
      return null;
    }
    Resource res = findResourceImpl(name);
    return res == null ? null : res.getURL();
  }

  @Nullable
  private Resource findResourceImpl(String name) {
    String n = FileUtilRt.toCanonicalPath(name, '/', false);
    Resource resource = getClassPath().getResource(n);
    if (resource == null && n.startsWith("/")) { // compatibility with existing code, non-standard classloader behavior
      resource = getClassPath().getResource(n.substring(1));
    }
    return resource;
  }

  @Nullable
  @Override
  public InputStream getResourceAsStream(String name) {
    if (myAllowBootstrapResources) {
      ourSkipFindingResource.set(Boolean.TRUE);
      try {
        InputStream stream = super.getResourceAsStream(name);
        if (stream != null) {
          return stream;
        }
      }
      finally {
        ourSkipFindingResource.remove();
      }
    }
    try {
      Resource res = findResourceImpl(name);
      return res == null ? null : res.getInputStream();
    }
    catch (IOException e) {
      return null;
    }
  }

  @Override
  protected Enumeration<URL> findResources(String name) {
    return getClassPath().getResources(name);
  }

  // called by a parent class on Java 7+
  @Override
  @NotNull
  protected Object getClassLoadingLock(String className) {
    return myClassLoadingLocks == null ? this : myClassLoadingLocks.getOrCreateLock(className);
  }
}