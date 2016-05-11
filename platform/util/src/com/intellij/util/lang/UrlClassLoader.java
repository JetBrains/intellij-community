/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.lang;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.WeakStringInterner;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * A class loader that allows for various customizations, e.g. not locking jars or using a special cache to speed up class loading.
 * Should be constructed using {@link #build()} method.
 */
public class UrlClassLoader extends ClassLoader {
  public static final String CLASS_EXTENSION = ".class";

  private static final boolean HAS_PARALLEL_LOADERS = SystemInfo.isJavaVersionAtLeast("1.7") && !SystemInfo.isIbmJvm;

  static {
    if (HAS_PARALLEL_LOADERS) {
      try {
        //todo Patches.USE_REFLECTION_TO_ACCESS_JDK7
        Method registerAsParallelCapable = ClassLoader.class.getDeclaredMethod("registerAsParallelCapable");
        registerAsParallelCapable.setAccessible(true);
        registerAsParallelCapable.invoke(null);
      }
      catch (Exception ignored) { }
    }
  }

  public static boolean isRegisteredAsParallelCapable(@NotNull ClassLoader loader) {
    if (!HAS_PARALLEL_LOADERS) return false;
    try {
      //todo Patches.USE_REFLECTION_TO_ACCESS_JDK7
      Field parallelLockMap = ClassLoader.class.getDeclaredField("parallelLockMap");
      parallelLockMap.setAccessible(true);
      return parallelLockMap.get(loader) != null;
    }
    catch (Exception e) {
      throw new AssertionError("Internal error: ClassLoader implementation has been altered");
    }
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

  public static final class Builder {
    private List<URL> myURLs = ContainerUtil.emptyList();
    private ClassLoader myParent = null;
    private boolean myLockJars = false;
    private boolean myUseCache = false;
    private boolean myUsePersistentClasspathIndex = false;
    private boolean myAcceptUnescaped = false;
    private boolean myPreload = true;
    private boolean myAllowBootstrapResources = false;
    @Nullable private CachePoolImpl myCachePool = null;
    @Nullable private CachingCondition myCachingCondition = null;

    private Builder() { }

    public Builder urls(List<URL> urls) { myURLs = urls; return this; }
    public Builder urls(URL... urls) { myURLs = Arrays.asList(urls); return this; }
    public Builder parent(ClassLoader parent) { myParent = parent; return this; }
    public Builder allowLock() { myLockJars = true; return this; }
    public Builder allowLock(boolean lockJars) { myLockJars = lockJars; return this; }
    public Builder useCache() { myUseCache = true; return this; }
    public Builder useCache(boolean useCache) { myUseCache = useCache; return this; }

    // Instruction for FileLoader to save list of files / packages under its root and use this information instead of walking filesystem for
    // speedier classloading. Should be used only when the caches could be properly invalidated, e.g. when new file appears under
    // FileLoader's root. Currently the flag is used for faster unit test / developed Idea running, because Idea's make (as of 14.1) ensures deletion of
    // such information upon appearing new file for output root.
    // N.b. Idea make does not ensure deletion of cached information upon deletion of some file under local root but false positives are not a
    // logical error since code is prepared for that and disk access is performed upon class / resource loading.
    // See also Builder#usePersistentClasspathIndexForLocalClassDirectories.
    public Builder usePersistentClasspathIndexForLocalClassDirectories() {
      myUsePersistentClasspathIndex = ourClassPathIndexEnabled;
      return this;
    }

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
    
    public Builder allowUnescaped() { return allowUnescaped(true); }
    public Builder allowUnescaped(boolean acceptUnescaped) { myAcceptUnescaped = acceptUnescaped; return this; }
    public Builder noPreload() { return preload(false); }
    public Builder preload(boolean preload) { myPreload = preload; return this; }
    public Builder allowBootstrapResources() { myAllowBootstrapResources = true; return this; }

    public UrlClassLoader get() { return new UrlClassLoader(this); }
  }

  public static Builder build() {
    return new Builder();
  }

  private final List<URL> myURLs;
  private final ClassPath myClassPath;
  private final WeakStringInterner myClassNameInterner;
  private final boolean myAllowBootstrapResources;

  /** @deprecated use {@link #build()}, left for compatibility with java.system.class.loader setting */
  public UrlClassLoader(@NotNull ClassLoader parent) {
    this(build().urls(((URLClassLoader)parent).getURLs()).parent(parent.getParent()).allowLock().useCache()
           .usePersistentClasspathIndexForLocalClassDirectories());
  }

  protected UrlClassLoader(@NotNull Builder builder) {
    super(builder.myParent);
    myURLs = ContainerUtil.map(builder.myURLs, new Function<URL, URL>() {
      @Override
      public URL fun(URL url) {
        return internProtocol(url);
      }
    });
    myClassPath = createClassPath(builder);
    myAllowBootstrapResources = builder.myAllowBootstrapResources;
    myClassNameInterner = isRegisteredAsParallelCapable(this) ? new WeakStringInterner() : null;
  }

  @NotNull
  protected final ClassPath createClassPath(@NotNull Builder builder) {
    return new ClassPath(myURLs, builder.myLockJars, builder.myUseCache, builder.myAcceptUnescaped, builder.myPreload,
                                builder.myUsePersistentClasspathIndex, builder.myCachePool, builder.myCachingCondition);
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
      Logger.getInstance(UrlClassLoader.class).error(e);
      return null;
    }
  }

  /** @deprecated to be removed in IDEA 15 */
  @SuppressWarnings({"unused", "deprecation"})
  public void addURL(URL url) {
    getClassPath().addURL(url);
    myURLs.add(url);
  }

  public List<URL> getUrls() {
    return Collections.unmodifiableList(myURLs);
  }

  @Override
  protected Class findClass(final String name) throws ClassNotFoundException {
    Resource res = getClassPath().getResource(name.replace('.', '/').concat(CLASS_EXTENSION), false);
    if (res == null) {
      throw new ClassNotFoundException(name);
    }

    try {
      return defineClass(name, res);
    }
    catch (IOException e) {
      throw new ClassNotFoundException(name, e);
    }
  }

  @Nullable
  protected Class _findClass(@NotNull String name) {
    Resource res = getClassPath().getResource(name.replace('.', '/').concat(CLASS_EXTENSION), false);
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
          // do nothing, package already defined by some other thread
        }
      }
    }

    byte[] b = res.getBytes();
    return _defineClass(name, b);
  }

  protected Class _defineClass(final String name, final byte[] b) {
    return defineClass(name, b, 0, b.length);
  }

  @Override
  @Nullable  // Accessed from PluginClassLoader via reflection // TODO do we need it?
  public URL findResource(final String name) {
    return findResourceImpl(name);
  }

  protected URL findResourceImpl(final String name) {
    Resource res = _getResource(name);
    return res != null ? res.getURL() : null;
  }

  @Nullable
  private Resource _getResource(final String name) {
    String n = name;
    n = StringUtil.trimStart(n, "/");
    return getClassPath().getResource(n, true);
  }

  @Nullable
  @Override
  public InputStream getResourceAsStream(final String name) {
    if (myAllowBootstrapResources) return super.getResourceAsStream(name);
    try {
      Resource res = _getResource(name);
      if (res == null) return null;
      return res.getInputStream();
    }
    catch (IOException e) {
      return null;
    }
  }

  // Accessed from PluginClassLoader via reflection // TODO do we need it?
  @Override
  protected Enumeration<URL> findResources(String name) throws IOException {
    return getClassPath().getResources(name, true);
  }

  public static void loadPlatformLibrary(@NotNull String libName) {
    String libFileName = mapLibraryName(libName);
    String libPath = PathManager.getBinPath() + "/" + libFileName;

    if (!new File(libPath).exists()) {
      String platform = getPlatformName();
      if (!new File(libPath = PathManager.getHomePath() + "/community/bin/" + platform + libFileName).exists()) {
        if (!new File(libPath = PathManager.getHomePath() + "/bin/" + platform + libFileName).exists()) {
          if (!new File(libPath = PathManager.getHomePathFor(IdeaWin32.class) + "/bin/" + libFileName).exists()) {
            File libDir = new File(PathManager.getBinPath());
            throw new UnsatisfiedLinkError("'" + libFileName + "' not found in '" + libDir + "' among " + Arrays.toString(libDir.list()));
          }
        }
      }
    }

    System.load(libPath);
  }

  private static String mapLibraryName(String libName) {
    String baseName = libName;
    if (SystemInfo.is64Bit) {
      baseName = baseName.replace("32", "") + "64";
    }
    String fileName = System.mapLibraryName(baseName);
    if (SystemInfo.isMac) {
      fileName = fileName.replace(".jnilib", ".dylib");
    }
    return fileName;
  }

  private static String getPlatformName() {
    if (SystemInfo.isWindows) return "win/";
    else if (SystemInfo.isMac) return "mac/";
    else if (SystemInfo.isLinux) return "linux/";
    else return "";
  }

  // called by a parent class on Java 7+
  @SuppressWarnings("unused")
  protected Object getClassLoadingLock(String className) {
    return myClassNameInterner != null ? myClassNameInterner.intern(new String(className)) : this;
  }

  /**
   * An interface for a pool to store internal class loader caches, that can be shared between several different class loaders,
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
   * @return a new pool to be able to share internal class loader caches between several different class loaders, if they contain the same URLs
   * in their class paths.
   */
  @NotNull 
  public static CachePool createCachePool() {
    return new CachePoolImpl();
  }
}