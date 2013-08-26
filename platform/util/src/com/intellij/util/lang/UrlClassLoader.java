/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.misc.Resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class UrlClassLoader extends ClassLoader {
  @NonNls static final String CLASS_EXTENSION = ".class";

  public static final class Builder {
    private List<URL> myURLs = ContainerUtil.emptyList();
    private ClassLoader myParent = null;
    private boolean myLockJars = false;
    private boolean myUseCache = false;
    private boolean myAcceptUnescaped = false;
    private boolean myPreload = true;

    private Builder() { }

    public Builder urls(List<URL> urls) { myURLs = urls; return this; }
    public Builder urls(URL... urls) { myURLs = Arrays.asList(urls); return this; }
    public Builder parent(ClassLoader parent) { myParent = parent; return this; }
    public Builder allowLock() { myLockJars = true; return this; }
    public Builder allowLock(boolean lockJars) { myLockJars = lockJars; return this; }
    public Builder useCache() { myUseCache = true; return this; }
    public Builder useCache(boolean useCache) { myUseCache = useCache; return this; }
    public Builder allowUnescaped() { myAcceptUnescaped = true; return this; }
    public Builder noPreload() { myPreload = false; return this; }
    public UrlClassLoader get() { return new UrlClassLoader(this); }
  }

  public static Builder build() {
    return new Builder();
  }

  private final List<URL> myURLs;
  private final ClassPath myClassPath;

  /** @deprecated use {@link #build()} (to remove in IDEA 14) */
  public UrlClassLoader(@NotNull ClassLoader parent) {
    this(build().urls(((URLClassLoader)parent).getURLs()).parent(parent.getParent()).allowLock().useCache());
  }

  /** @deprecated use {@link #build()} (to remove in IDEA 14) */
  public UrlClassLoader(List<URL> urls, @Nullable ClassLoader parent) {
    this(build().urls(urls).parent(parent));
  }

  /** @deprecated use {@link #build()} (to remove in IDEA 14) */
  public UrlClassLoader(URL[] urls, @Nullable ClassLoader parent) {
    this(build().urls(urls).parent(parent));
  }

  /** @deprecated use {@link #build()} (to remove in IDEA 14) */
  public UrlClassLoader(List<URL> urls, @Nullable ClassLoader parent, boolean lockJars, boolean useCache) {
    this(build().urls(urls).parent(parent).allowLock(lockJars).useCache(useCache));
  }

  /** @deprecated use {@link #build()} (to remove in IDEA 14) */
  public UrlClassLoader(List<URL> urls, @Nullable ClassLoader parent, boolean lockJars, boolean useCache, boolean allowUnescaped, boolean preload) {
    super(parent);
    myURLs = ContainerUtil.map(urls, new Function<URL, URL>() {
      @Override
      public URL fun(URL url) {
        return internProtocol(url);
      }
    });
    myClassPath = new ClassPath(myURLs, lockJars, useCache, allowUnescaped, preload);
  }

  protected UrlClassLoader(@NotNull Builder builder) {
    super(builder.myParent);
    myURLs = ContainerUtil.map(builder.myURLs, new Function<URL, URL>() {
      @Override
      public URL fun(URL url) {
        return internProtocol(url);
      }
    });
    myClassPath = new ClassPath(myURLs, builder.myLockJars, builder.myUseCache, builder.myAcceptUnescaped, builder.myPreload);
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

  public void addURL(URL url) {
    myClassPath.addURL(url);
    myURLs.add(url);
  }

  public List<URL> getUrls() {
    return Collections.unmodifiableList(myURLs);
  }

  @Override
  protected Class findClass(final String name) throws ClassNotFoundException {
    Resource res = myClassPath.getResource(name.replace('.', '/').concat(CLASS_EXTENSION), false);
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
    Resource res = myClassPath.getResource(name.replace('.', '/').concat(CLASS_EXTENSION), false);
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
          definePackage(pkgName, null, null, null, null, null, null, null);
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
    if (n.startsWith("/")) n = n.substring(1);
    return myClassPath.getResource(n, true);
  }

  @Nullable
  @Override
  public InputStream getResourceAsStream(final String name) {
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
    return myClassPath.getResources(name, true);
  }

  public static void loadPlatformLibrary(@NotNull String libName) {
    String libFileName = mapLibraryName(libName);
    String libPath = PathManager.getBinPath() + "/" + libFileName;

    if (!new File(libPath).exists()) {
      String platform = getPlatformName();
      if (!new File(libPath = PathManager.getHomePath() + "/community/bin/" + platform + libFileName).exists()) {
        if (!new File(libPath = PathManager.getHomePath() + "/bin/" + platform + libFileName).exists()) {
          if (!new File(libPath = PathManager.getHomePathFor(IdeaWin32.class) + "/bin/" + libFileName).exists()) {
            throw new UnsatisfiedLinkError("'" + libFileName + "' not found among " + Arrays.toString(new File(PathManager.getBinPath()).listFiles()));
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
}
