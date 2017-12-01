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
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.Stack;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;

public class ClassPath {
  private static final ResourceStringLoaderIterator ourCheckedIterator = new ResourceStringLoaderIterator(true);
  private static final ResourceStringLoaderIterator ourUncheckedIterator = new ResourceStringLoaderIterator(false);
  private static final LoaderCollector ourLoaderCollector = new LoaderCollector();

  private final Stack<URL> myUrls = new Stack<URL>();
  private final List<Loader> myLoaders = new ArrayList<Loader>();

  private volatile boolean myAllUrlsWereProcessed;

  private final AtomicInteger myLastLoaderProcessed = new AtomicInteger();
  private final Map<URL, Loader> myLoadersMap = new HashMap<URL, Loader>();
  private final ClasspathCache myCache = new ClasspathCache();

  private final boolean myCanLockJars;
  private final boolean myCanUseCache;
  private final boolean myAcceptUnescapedUrls;
  private final boolean myPreloadJarContents;
  private final boolean myCanHavePersistentIndex;
  @Nullable private final CachePoolImpl myCachePool;
  @Nullable private final UrlClassLoader.CachingCondition myCachingCondition;

  public ClassPath(List<URL> urls,
                   boolean canLockJars,
                   boolean canUseCache,
                   boolean acceptUnescapedUrls,
                   boolean preloadJarContents,
                   boolean canHavePersistentIndex,
                   @Nullable CachePoolImpl cachePool,
                   @Nullable UrlClassLoader.CachingCondition cachingCondition) {
    myCanLockJars = canLockJars;
    myCanUseCache = canUseCache;
    myAcceptUnescapedUrls = acceptUnescapedUrls;
    myPreloadJarContents = preloadJarContents;
    myCachePool = cachePool;
    myCachingCondition = cachingCondition;
    myCanHavePersistentIndex = canHavePersistentIndex;
    push(urls);
  }

  /**
   * @deprecated Adding additional urls to classpath at runtime could lead to hard-to-debug errors
   */
  @Deprecated
  void addURL(URL url) {
    push(Collections.singletonList(url));
  }

  private void push(List<URL> urls) {
    if (!urls.isEmpty()) {
      synchronized (myUrls) {
        for (int i = urls.size() - 1; i >= 0; i--) {
          myUrls.push(urls.get(i));
        }
        myAllUrlsWereProcessed = false;
      }
    }
  }

  @Nullable
  public Resource getResource(String s, boolean flag) {
    final long started = startTiming();
    try {
      int i;
      if (myCanUseCache) {
        boolean allUrlsWereProcessed = myAllUrlsWereProcessed;
        i = allUrlsWereProcessed ? 0 : myLastLoaderProcessed.get();

        Resource prevResource = myCache.iterateLoaders(s, flag ? ourCheckedIterator : ourUncheckedIterator, s, this);
        if (prevResource != null || allUrlsWereProcessed) return prevResource;
      }
      else {
        i = 0;
      }

      String shortName = ClasspathCache.transformName(s);

      Loader loader;
      while ((loader = getLoader(i++)) != null) {
        if (myCanUseCache) {
          if (!myCache.loaderHasName(s, shortName, loader)) continue;
        }
        Resource resource = loader.getResource(s, flag);
        if (resource != null) {
          return resource;
        }
      }
    }
    finally {
      logTiming(this, started, s);
    }

    return null;
  }

  public Enumeration<URL> getResources(final String name, final boolean check) {
    return new MyEnumeration(name, check);
  }

  @Nullable
  private synchronized Loader getLoader(int i) {
    while (myLoaders.size() < i + 1) {
      boolean lastOne;
      URL url;
      synchronized (myUrls) {
        if (myUrls.empty()) {
          if (myCanUseCache) {
            myCache.nameSymbolsLoaded();
            myAllUrlsWereProcessed = true;
          }
          return null;
        }
        url = myUrls.pop();
        lastOne = myUrls.isEmpty();
      }

      if (myLoadersMap.containsKey(url)) continue;

      try {
        initLoaders(url, lastOne, myLoaders.size());
      }
      catch (IOException e) {
        Logger.getInstance(ClassPath.class).info("url: " + url, e);
      }
    }

    return myLoaders.get(i);
  }

  public List<URL> getBaseUrls() {
    List<URL> result = new ArrayList<URL>();
    for (Loader loader : myLoaders) {
      result.add(loader.getBaseURL());
    }
    return result;
  }

  private void initLoaders(final URL url, boolean lastOne, int index) throws IOException {
    String path;

    if (myAcceptUnescapedUrls) {
      path = url.getFile();
    }
    else {
      try {
        path = url.toURI().getSchemeSpecificPart();
      }
      catch (URISyntaxException e) {
        Logger.getInstance(ClassPath.class).error("url: " + url, e);
        path = url.getFile();
      }
    }

    if (path != null && URLUtil.FILE_PROTOCOL.equals(url.getProtocol())) {
      Loader loader = createLoader(url, index, new File(path), true);
      if (loader != null) {
        initLoader(url, lastOne, loader);
      }
    }
  }

  private Loader createLoader(URL url, int index, File file, boolean processRecursively) throws IOException {
    if (file.isDirectory()) {
      return new FileLoader(url, index, myCanHavePersistentIndex);
    }
    if (file.isFile()) {
      Loader loader = new JarLoader(url, myCanLockJars, index, myPreloadJarContents, this);
      if (processRecursively) {
        String[] referencedJars = loadManifestClasspath((JarLoader)loader);
        if (referencedJars != null) {
          for (String referencedJar : referencedJars) {
            try {
              URI uri = new URI(referencedJar);
              File referencedFile = new File(uri);
              URL referencedUrl = uri.toURL();
              Loader referencedLoader = createLoader(referencedUrl, index++, referencedFile, false);
              if (referencedLoader != null) {
                initLoader(referencedUrl, false, referencedLoader);
              }
            }
            catch (Exception e) {
              Logger.getInstance(ClassPath.class).warn("url: " + url + " / " + referencedJar, e);
            }
          }
        }
      }
      return loader;
    }
    return null;
  }

  private void initLoader(URL url, boolean lastOne, Loader loader) throws IOException {
    if (myCanUseCache) {
      ClasspathCache.LoaderData data = myCachePool == null ? null : myCachePool.getCachedData(url);
      if (data == null) {
        data = loader.buildData();
        if (myCachePool != null && myCachingCondition != null && myCachingCondition.shouldCacheData(url)) {
          myCachePool.cacheData(url, data);
        }
      }
      myCache.applyLoaderData(data, loader);

      if (lastOne) {
        myCache.nameSymbolsLoaded();
        myAllUrlsWereProcessed = true;
      }
      myLastLoaderProcessed.incrementAndGet();
      //assert myLastLoaderProcessed.get() == myLoaders.size();
    }
    myLoaders.add(loader);
    myLoadersMap.put(url, loader);
  }

  Attributes getManifestData(URL url) {
    return myCanUseCache && myCachePool != null ? myCachePool.getManifestData(url) : null;
  }

  void cacheManifestData(URL url, Attributes manifestAttributes) {
    if (myCanUseCache && myCachePool != null && myCachingCondition != null && myCachingCondition.shouldCacheData(url)) {
      myCachePool.cacheManifestData(url, manifestAttributes);
    }
  }

  private class MyEnumeration implements Enumeration<URL> {
    private int myIndex;
    private Resource myRes;
    private final String myName;
    private final String myShortName;
    private final boolean myCheck;
    private final List<Loader> myLoaders;

    public MyEnumeration(String name, boolean check) {
      myName = name;
      myShortName = ClasspathCache.transformName(name);
      myCheck = check;
      List<Loader> loaders = null;

      if (myCanUseCache && myAllUrlsWereProcessed) {
        Collection<Loader> loadersSet = new LinkedHashSet<Loader>();
        myCache.iterateLoaders(name, ourLoaderCollector, loadersSet, this);

        if (name.endsWith("/")) {
          myCache.iterateLoaders(name.substring(0, name.length() - 1), ourLoaderCollector, loadersSet, this);
        } else {
          myCache.iterateLoaders(name + "/", ourLoaderCollector, loadersSet, this);
        }

        loaders = new ArrayList<Loader>(loadersSet);
      }

      myLoaders = loaders;
    }

    private boolean next() {
      if (myRes != null) return true;

      long started = startTiming();
      try {
        Loader loader;
        if (myLoaders != null) {
          while (myIndex < myLoaders.size()) {
            loader = myLoaders.get(myIndex++);
            if (!myCache.loaderHasName(myName, myShortName, loader)) {
              myRes = null;
              continue;
            }
            myRes = loader.getResource(myName, myCheck);
            if (myRes != null) return true;
          }
        }
        else {
          while ((loader = getLoader(myIndex++)) != null) {
            if (myCanUseCache && !myCache.loaderHasName(myName, myShortName, loader)) continue;
            myRes = loader.getResource(myName, myCheck);
            if (myRes != null) return true;
          }
        }
      }
      finally {
        logTiming(ClassPath.this, started, myName);
      }

      return false;
    }

    @Override
    public boolean hasMoreElements() {
      return next();
    }

    @Override
    public URL nextElement() {
      if (!next()) {
        throw new NoSuchElementException();
      }
      else {
        Resource resource = myRes;
        myRes = null;
        return resource.getURL();
      }
    }
  }

  private static class ResourceStringLoaderIterator extends ClasspathCache.LoaderIterator<Resource, String, ClassPath> {
    private final boolean myFlag;

    private ResourceStringLoaderIterator(boolean flag) {
      myFlag = flag;
    }

    @Override
    Resource process(Loader loader, String s, ClassPath classPath) {
      if (!classPath.myCache.loaderHasName(s, ClasspathCache.transformName(s), loader)) return null;
      Resource resource = loader.getResource(s, myFlag);
      if (resource != null) printOrder(loader, s, resource);
      return resource;
    }
  }

  private static class LoaderCollector extends ClasspathCache.LoaderIterator<Object, Collection<Loader>, Object> {
    @Override
    Object process(Loader loader, Collection<Loader> parameter, Object parameter2) {
      parameter.add(loader);
      return null;
    }
  }

  private static final boolean ourDumpOrder = "true".equals(System.getProperty("idea.dump.order"));
  private static PrintStream ourOrder;
  private static long ourOrderSize;
  private static final Set<String> ourOrderedUrls = new HashSet<String>();

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static synchronized void printOrder(Loader loader, String url, Resource resource) {
    if (!ourDumpOrder) return;
    if (!ourOrderedUrls.add(url)) return;

    String home = FileUtil.toSystemIndependentName(PathManager.getHomePath());
    try {
      if (resource instanceof MemoryResource) {
        ourOrderSize += resource.getBytes().length;
      }
    }
    catch (IOException e) {
      e.printStackTrace(System.out);
    }

    if (ourOrder == null) {
      final File orderFile = new File(PathManager.getBinPath(), "order.txt");
      try {
        if (!FileUtil.ensureCanCreateFile(orderFile)) return;
        ourOrder = new PrintStream(new FileOutputStream(orderFile, true));
        ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
          @Override
          public void run() {
            closeOrderStream();
          }
        });
      }
      catch (IOException e) {
        return;
      }
    }

    if (ourOrder != null) {
      String jarURL = FileUtil.toSystemIndependentName(loader.getBaseURL().getFile());
      jarURL = StringUtil.trimStart(jarURL, "file:/");
      if (jarURL.startsWith(home)) {
        jarURL = jarURL.replaceFirst(home, "");
        jarURL = StringUtil.trimEnd(jarURL, "!/");
        ourOrder.println(url + ":" + jarURL);
      }
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static synchronized void closeOrderStream() {
    ourOrder.close();
    System.out.println(ourOrderSize);
  }

  private static final boolean ourLogTiming = Boolean.getBoolean("idea.print.classpath.timing");
  private static long ourTotalTime;
  private static int ourTotalRequests;

  private static long startTiming() {
    return ourLogTiming ? System.nanoTime() : 0;
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void logTiming(ClassPath path, long started, String msg) {
    if (!ourLogTiming) return;

    long time = System.nanoTime() - started;
    ourTotalTime += time;
    ++ourTotalRequests;
    if (time > 10000000L) {
      System.out.println(time / 1000000 + " ms for " + msg);
    }
    if (ourTotalRequests % 1000 == 0) {
      System.out.println(path + ", requests:" + ourTotalRequests + ", time:" + (ourTotalTime / 1000000) + "ms");
    }
  }

  private static String[] loadManifestClasspath(JarLoader loader) {
    try {
      String classPath = loader.getManifestAttributes().getValue(Attributes.Name.CLASS_PATH);

      if (classPath != null) {
        String[] urls = classPath.split(" ");
        if (urls.length > 0 && urls[0].startsWith("file:")) {
          return urls;
        }
      }
    }
    catch (Exception ignore) { }
    return null;
  }
}