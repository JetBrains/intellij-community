// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.openapi.diagnostic.LoggerRt;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
public class UrlClassLoader extends ClassLoader {
  static final String CLASS_EXTENSION = ".class";

  private static final Set<Class<?>> ourParallelCapableLoaders;
  static {
    //this class is compiled for Java 6 so it's enough to check that it isn't running under Java 6
    boolean isAtLeastJava7 = !System.getProperty("java.runtime.version", "unknown").startsWith("1.6.");

    boolean ibmJvm = System.getProperty("java.vm.vendor", "unknown").toLowerCase(Locale.ENGLISH).contains("ibm");
    boolean capable =
      isAtLeastJava7 && !ibmJvm && Boolean.parseBoolean(System.getProperty("use.parallel.class.loading", "true"));
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

  protected static void markParallelCapable(Class<? extends UrlClassLoader> loaderClass) {
    assert ourParallelCapableLoaders != null;
    ourParallelCapableLoaders.add(loaderClass);
  }

  /**
   * Called by the VM to support dynamic additions to the class path
   *
   * @see java.lang.instrument.Instrumentation#appendToSystemClassLoaderSearch
   */
  @SuppressWarnings("unused")
  void appendToClassPathForInstrumentation(String jar) {
    try {
      addURL(new File(jar).toURI().toURL());
    } catch(MalformedURLException ignore) {}
  }

  private static final boolean ourClassPathIndexEnabled = Boolean.parseBoolean(System.getProperty("idea.classpath.index.enabled", "true"));

  @NotNull
  protected ClassPath getClassPath() {
    return myClassPath;
  }

  /**
   * See com.intellij.TestAll#getClassRoots()
   */
  @SuppressWarnings("unused")
  public List<URL> getBaseUrls() {
    return myClassPath.getBaseUrls();
  }

  @SuppressWarnings("unused")  // called via reflection
  public Collection<String> getJarAccessLog() {
    return myClassPath.getJarAccessLog();
  }

  public static final class Builder {
    private List<URL> myURLs = ContainerUtilRt.emptyList();
    private Set<URL> myURLsWithProtectionDomain = new HashSet<URL>();
    private ClassLoader myParent;
    private boolean myLockJars;
    private boolean myUseCache;
    private boolean myUsePersistentClasspathIndex;
    private boolean myAcceptUnescaped;
    private boolean myPreload = true;
    private boolean myAllowBootstrapResources;
    private boolean myErrorOnMissingJar = true;
    private boolean myLazyClassloadingCaches;
    private boolean myLogJarAccess;
    private @Nullable CachePoolImpl myCachePool;
    private @Nullable CachingCondition myCachingCondition;

    Builder() { }

    public Builder urls(@NotNull List<URL> urls) { myURLs = urls; return this; }
    public Builder urls(@NotNull URL... urls) { myURLs = Arrays.asList(urls); return this; }
    public Builder parent(ClassLoader parent) { myParent = parent; return this; }

    /**
     * @param urls List of URLs that are signed by Sun/Oracle and their signatures must be verified.
     */
    public Builder urlsWithProtectionDomain(@NotNull Set<URL> urls) { myURLsWithProtectionDomain = urls; return this; }

    /**
     * @see #urlsWithProtectionDomain(Set)
     */
    public Builder urlsWithProtectionDomain(@NotNull URL... urls) { return urlsWithProtectionDomain(ContainerUtilRt.newHashSet(urls)); }

    /**
     * ZipFile handles opened in JarLoader will be kept in SoftReference. Depending on OS, the option significantly speeds up classloading
     * from libraries. Caveat: for Windows opened handle will lock the file preventing its modification.
     * Thus, the option is recommended when jars are not modified or process that uses this option is transient.
     */
    public Builder allowLock() { myLockJars = true; return this; }
    public Builder allowLock(boolean lockJars) { myLockJars = lockJars; return this; }

    /**
     * Build backward index of packages / class or resource names that allows avoiding IO during classloading.
     */
    public Builder useCache() { myUseCache = true; return this; }
    public Builder useCache(boolean useCache) { myUseCache = useCache; return this; }

    /**
     * FileLoader will save list of files / packages under its root and use this information instead of walking filesystem for
     * speedier classloading. Should be used only when the caches could be properly invalidated, e.g. when new file appears under
     * FileLoader's root. Currently the flag is used for faster unit test / developed Idea running, because Idea's make (as of 14.1) ensures deletion of
     * such information upon appearing new file for output root.
     * N.b. Idea make does not ensure deletion of cached information upon deletion of some file under local root but false positives are not a
     * logical error since code is prepared for that and disk access is performed upon class / resource loading.
     * See also Builder#usePersistentClasspathIndexForLocalClassDirectories.
     */
    public Builder usePersistentClasspathIndexForLocalClassDirectories() {
      myUsePersistentClasspathIndex = ourClassPathIndexEnabled;
      return this;
    }

    public Builder logJarAccess(boolean logJarAccess) { myLogJarAccess = logJarAccess; return this; }

    /**
     * Requests the class loader being built to use cache and, if possible, retrieve and store the cached data from a special cache pool
     * that can be shared between several loaders.

     * @param pool cache pool
     * @param condition a custom policy to provide a possibility to prohibit caching for some URLs.
     * @return this instance
     *
     * @see #createCachePool()
     */
    public Builder useCache(@NotNull CachePool pool, @NotNull CachingCondition condition) {
      myUseCache = true;
      myCachePool = (CachePoolImpl)pool;
      myCachingCondition = condition;
      return this;
    }

    public Builder allowUnescaped() { myAcceptUnescaped = true; return this; }
    public Builder noPreload() { myPreload = false; return this; }
    public Builder allowBootstrapResources() { myAllowBootstrapResources = true; return this; }
    public Builder setLogErrorOnMissingJar(boolean log) { myErrorOnMissingJar = log; return this; }

    /**
     * Package contents information in Jar/File loaders will be lazily retrieved / cached upon classloading.
     * Important: this option will result in much smaller initial overhead but for bulk classloading (like complete IDE start) it is less
     * efficient (in number of disk / native code accesses / CPU spent) than combination of useCache / usePersistentClasspathIndexForLocalClassDirectories.
     */
    public Builder useLazyClassloadingCaches(boolean pleaseBeLazy) { myLazyClassloadingCaches = pleaseBeLazy; return this; }

    @NotNull
    public UrlClassLoader get() {
      return new UrlClassLoader(this);
    }
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
  public UrlClassLoader(@NotNull ClassLoader parent) {
    this(build().urls(((URLClassLoader)parent).getURLs()).parent(parent.getParent()).allowLock().useCache()
           .usePersistentClasspathIndexForLocalClassDirectories()
           .useLazyClassloadingCaches(Boolean.parseBoolean(System.getProperty("idea.lazy.classloading.caches", "false"))));
  }

  protected UrlClassLoader(@NotNull Builder builder) {
    super(builder.myParent);
    myURLs = ContainerUtilRt.map2List(builder.myURLs, new Function<URL, URL>() {
      @Override
      public URL fun(URL url) {
        return internProtocol(url);
      }
    });
    myClassPath = createClassPath(builder);
    myAllowBootstrapResources = builder.myAllowBootstrapResources;
    myClassLoadingLocks = ourParallelCapableLoaders != null && ourParallelCapableLoaders.contains(getClass()) ? new ClassLoadingLocks() : null;
  }

  @NotNull
  protected final ClassPath createClassPath(@NotNull Builder builder) {
    return new ClassPath(myURLs, builder.myLockJars, builder.myUseCache, builder.myAcceptUnescaped, builder.myPreload,
                         builder.myUsePersistentClasspathIndex, builder.myCachePool, builder.myCachingCondition,
                         builder.myErrorOnMissingJar, builder.myLazyClassloadingCaches, builder.myURLsWithProtectionDomain,
                         builder.myLogJarAccess);
  }

  public static URL internProtocol(@NotNull URL url) {
    try {
      final String protocol = url.getProtocol();
      if ("file".equals(protocol) || "jar".equals(protocol)) {
        return new URL(protocol.intern(), url.getHost(), url.getPort(), url.getFile());
      }
      return url;
    }
    catch (MalformedURLException e) {
      LoggerRt.getInstance(UrlClassLoader.class).error(e);
      return null;
    }
  }

  /** @deprecated adding URLs to a classloader at runtime could lead to hard-to-debug errors */
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  public void addURL(@NotNull URL url) {
    getClassPath().addURL(internProtocol(url));
    myURLs.add(url);
  }

  public List<URL> getUrls() {
    return Collections.unmodifiableList(myURLs);
  }

  public boolean hasLoadedClass(String name) {
    Class<?> aClass = findLoadedClass(name);
    return aClass != null && aClass.getClassLoader() == this;
  }

  @Override
  protected Class findClass(final String name) throws ClassNotFoundException {
    Class clazz = _findClass(name);
    if (clazz == null) {
      throw new ClassNotFoundException(name);
    }
    return clazz;
  }

  @Nullable
  protected final Class _findClass(@NotNull String name) {
    Resource res = getClassPath().getResource(name.replace('.', '/') + CLASS_EXTENSION);
    if (res == null) {
      return null;
    }
    try {
      return defineClass(name, res);
    }
    catch (IOException e) {
      return null;
    }
  }

  private Class defineClass(String name, Resource res) throws IOException {
    int i = name.lastIndexOf('.');
    if (i != -1) {
      String pkgName = name.substring(0, i);
      // Check if package already loaded.
      Package pkg = getPackage(pkgName);
      if (pkg == null) {
        try {
          definePackage(pkgName,
                        res.getValue(Resource.Attribute.SPEC_TITLE),
                        res.getValue(Resource.Attribute.SPEC_VERSION),
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
    else {
      return _defineClass(name, b);
    }
  }

  protected Class _defineClass(final String name, final byte[] b) {
    return defineClass(name, b, 0, b.length);
  }

  protected Class _defineClass(final String name, final byte[] b, @Nullable ProtectionDomain protectionDomain) {
    return defineClass(name, b, 0, b.length, protectionDomain);
  }

  private static final ThreadLocal<Boolean> ourSkipFindingResource = new ThreadLocal<Boolean>();

  @Override
  public URL findResource(String name) {
    if (ourSkipFindingResource.get() != null) return null;
    Resource res = findResourceImpl(name);
    return res != null ? res.getURL() : null;
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
        if (stream != null) return stream;
      } finally {
        ourSkipFindingResource.set(null);
      }
    }
    try {
      Resource res = findResourceImpl(name);
      return res != null ? res.getInputStream() : null;
    }
    catch (IOException e) {
      return null;
    }
  }

  @Override
  protected Enumeration<URL> findResources(String name) throws IOException {
    return getClassPath().getResources(name);
  }

  // called by a parent class on Java 7+
  @SuppressWarnings("unused")
  @NotNull
  protected Object getClassLoadingLock(String className) {
    //noinspection RedundantStringConstructorCall
    return myClassLoadingLocks != null ? myClassLoadingLocks.getOrCreateLock(className) : this;
  }

  /**
   * An interface for a pool to store internal caches that can be shared between different class loaders,
   * if they contain the same URLs in their class paths.<p/>
   *
   * The implementation is subject to change so one shouldn't rely on it.
   *
   * @see #createCachePool()
   * @see Builder#useCache(CachePool, CachingCondition)
   */
  public interface CachePool { }

  /**
   * A condition to customize the caching policy when using {@link CachePool}. This might be needed when a class loader is used on a directory
   * that's being written into, to avoid the situation when a resource path is cached as nonexistent but then a file actually appears there,
   * and other class loaders with the same caching pool should have access to these new resources. This can happen during compilation process
   * with several module outputs.
   */
  public interface CachingCondition {
    /**
     * @return whether the internal information should be cached for files in a specific classpath component URL: inside the directory or
     * a jar.
     */
    boolean shouldCacheData(@NotNull URL url);
  }

  /**
   * @return a new pool to be able to share internal caches between different class loaders, if they contain the same URLs
   * in their class paths.
   */
  @NotNull
  public static CachePool createCachePool() {
    return new CachePoolImpl();
  }
}