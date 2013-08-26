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
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import sun.misc.Resource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

public class ClassPath {
  private final Stack<URL> myUrls = new Stack<URL>();
  private final ArrayList<Loader> myLoaders = new ArrayList<Loader>();
  private final HashMap<URL,Loader> myLoadersMap = new HashMap<URL, Loader>();
  private final ClasspathCache myCache = new ClasspathCache();

  @NonNls private static final String FILE_PROTOCOL = "file";
  private static final boolean myDebugTime = false;
  private static final boolean ourDumpOrder = "true".equals(System.getProperty("idea.dump.order"));
//  private static final boolean ourPreloadClasses = "true".equals(System.getProperty("idea.preload.classes"));

  private final boolean myCanLockJars;
  private final boolean myCanUseCache;
  private static final long NS_THRESHOLD = 10000000L;
  private static long total;
  private static int requests;

  private static PrintStream ourOrder;
  private static long ourOrderSize;
  private final static Set<String> ourOrderedUrls = new HashSet<String>();
  private static final String HOME = FileUtil.toSystemIndependentName(PathManager.getHomePath());

  private final boolean myAcceptUnescapedUrls;
  private final boolean myPreloadJarContents;

  private static synchronized void printOrder(Loader loader, String url, Resource resource) {
    if (!ourOrderedUrls.add(url)) return;
    try {
      ourOrderSize += resource.getContentLength();
    }
    catch (IOException e) {
      System.out.println(e);
    }
    if (ourOrder == null) {
      final File orderFile = new File(PathManager.getBinPath() + File.separator + "order.txt");
      try {
        if (!FileUtil.ensureCanCreateFile(orderFile)) return;
        ourOrder = new PrintStream(new FileOutputStream(orderFile, true));
        ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
          public void run() {
            ourOrder.close();
            System.out.println(ourOrderSize);
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
      if (jarURL.startsWith(HOME)) {
        jarURL = jarURL.replaceFirst(HOME, "");
        jarURL = StringUtil.trimEnd(jarURL, "!/");
        ourOrder.println(url + ":" + jarURL);
      }
    }
  }

  /** @deprecated use {@link #ClassPath(java.util.List, boolean, boolean, boolean, boolean)} (to remove in IDEA 14) */
  public ClassPath(URL[] urls, boolean canLockJars, boolean canUseCache) {
    this(Arrays.asList(urls), canLockJars, canUseCache, false, true);
  }

  /** @deprecated use {@link #ClassPath(java.util.List, boolean, boolean, boolean, boolean)} (to remove in IDEA 14) */
  public ClassPath(URL[] urls, boolean canLockJars, boolean canUseCache, boolean acceptUnescapedUrls, boolean preloadJarContents) {
    this(Arrays.asList(urls), canLockJars, canUseCache, acceptUnescapedUrls, preloadJarContents);
  }

  public ClassPath(List<URL> urls, boolean canLockJars, boolean canUseCache, boolean acceptUnescapedUrls, boolean preloadJarContents) {
    myCanLockJars = canLockJars;
    myCanUseCache = canUseCache;
    myAcceptUnescapedUrls = acceptUnescapedUrls;
    myPreloadJarContents = preloadJarContents;
    push(urls);
  }

  // Accessed by reflection from PluginClassLoader // TODO: do we need it?
  void addURL(URL url) {
    push(Collections.singletonList(url));
  }

  private void push(List<URL> urls) {
    if (!urls.isEmpty()) {
      synchronized (myUrls) {
        for (int i = urls.size() - 1; i >= 0; i--) {
          myUrls.push(urls.get(i));
        }
      }
    }
  }

  @Nullable
  public Resource getResource(String s, boolean flag) {
    final long started = myDebugTime ? System.nanoTime():0;

    try {
      int i;
      if (myCanUseCache) {
        Resource prevResource = myCache.iterateLoaders(s, flag ? checkedIterator:uncheckedIterator, s, this);
        if (prevResource != null) return prevResource;

        synchronized (myUrls) {
          if (myUrls.isEmpty()) return null;
        }

        i = myLoaders.size();
      }
      else {
        i = 0;
      }

      for (Loader loader; (loader = getLoader(i)) != null; i++) {
        if (myCanUseCache) {
          if (!myCache.loaderHasName(s, loader)) continue;
        }
        Resource resource = loader.getResource(s, flag);
        if (resource != null) {
          return resource;
        }
      }

      return null;
    }
    finally {
      if (myDebugTime) reportTime(started, s);
    }
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
          if (myCanUseCache) myCache.nameSymbolsLoaded();
          return null;
        }
        url = myUrls.pop();
        lastOne = myUrls.isEmpty();
      }

      if (myLoadersMap.containsKey(url)) continue;

      Loader loader;
      try {
        loader = getLoader(url, myLoaders.size());
        if (loader == null) continue;
      }
      catch (IOException ioexception) {
        continue;
      }

      myLoaders.add(loader);
      myLoadersMap.put(url, loader);
      if (lastOne && myCanUseCache) {
        myCache.nameSymbolsLoaded();
      }
    }

    return myLoaders.get(i);
  }

  @Nullable
  private Loader getLoader(final URL url, int index) throws IOException {
    String s;
    if (myAcceptUnescapedUrls) {
      s = url.getFile();
    } else {
      try {
        s = url.toURI().getSchemeSpecificPart();
      } catch (URISyntaxException thisShouldNotHappen) {
        thisShouldNotHappen.printStackTrace();
        s = url.getFile();
      }
    }

    Loader loader = null;
    if (s != null  && new File(s).isDirectory()) {
      if (FILE_PROTOCOL.equals(url.getProtocol())) {
        loader = new FileLoader(url, index);
      }
    }
    else {
      JarLoader jarLoader = new JarLoader(url, myCanLockJars, index);
      if (myPreloadJarContents) {
        jarLoader.preLoadClasses();
      }
      loader = jarLoader;
    }

    if (loader != null && myCanUseCache) {
      try {
        loader.buildCache(myCache);
      }
      catch (Throwable e) {
        // TODO: log can't create loader
      }
    }

    return loader;
  }

  private class MyEnumeration implements Enumeration<URL> {
    private int myIndex = 0;
    private Resource myRes = null;
    private final String myName;
    private final boolean myCheck;
    private final List<Loader> myLoaders;

    public MyEnumeration(String name, boolean check) {
      myName = name;
      myCheck = check;
      List<Loader> loaders = null;

      if (myCanUseCache) {
        synchronized (myUrls) {
          if (myUrls.isEmpty()) {
            loaders = new SmartList<Loader>();
            myCache.iterateLoaders(name, myLoaderCollector, loaders, this);
            if (!name.endsWith("/")) {
              myCache.iterateLoaders(name.concat("/"), myLoaderCollector, loaders, this);
            }
          }
        }
      }

      myLoaders = loaders;
    }

    private boolean next() {
      if (myRes != null) return true;
      long started = myDebugTime ? System.nanoTime() : 0;
      Loader loader;
      try {
        if (myLoaders != null) {
          while (myIndex < myLoaders.size()) {
            loader = myLoaders.get(myIndex++);
            if (!myCache.loaderHasName(myName, loader)) {
              myRes = null;
              continue;
            }
            myRes = loader.getResource(myName, myCheck);
            if (myRes != null) return true;
          }
        }
        else {
          while ((loader = getLoader(myIndex++)) != null) {
            if (!myCache.loaderHasName(myName, loader)) continue;
            myRes = loader.getResource(myName, myCheck);
            if (myRes != null) return true;
          }
        }
      }
      finally {
        if (myDebugTime) reportTime(started, myName);
      }


      return false;
    }

    public boolean hasMoreElements() {
      return next();
    }

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

  private void reportTime(long started, String msg) {
    long doneFor = System.nanoTime() - started;
    total += doneFor;
    ++requests;
    if (doneFor > NS_THRESHOLD) {
      System.out.println((doneFor/1000000) + " ms for " +msg);
    }
    if (requests % 1000 == 0) {
      System.out.println(toString() + ", requests:" + requests + ", time:" + (total / 1000000) + "ms");
    }
  }

  private static class ResourceStringLoaderIterator extends ClasspathCache.LoaderIterator<Resource, String, ClassPath> {
    private final boolean myFlag;

    private ResourceStringLoaderIterator(boolean flag) {
      myFlag = flag;
    }

    @Override
    Resource process(Loader loader, String s, ClassPath classPath) {
      if (!classPath.myCache.loaderHasName(s, loader)) return null;
      final Resource resource = loader.getResource(s, myFlag);
      if (resource != null) {
        if (ourDumpOrder) {
          printOrder(loader, s, resource);
        }
        return resource;
      }
      return null;
    }
  }
  private static final ResourceStringLoaderIterator checkedIterator = new ResourceStringLoaderIterator(true);
  private static final ResourceStringLoaderIterator uncheckedIterator = new ResourceStringLoaderIterator(false);
  private final static LoaderCollector myLoaderCollector = new LoaderCollector();

  private static class LoaderCollector extends ClasspathCache.LoaderIterator<Object, List<Loader>, Object> {
    @Override
    Object process(Loader loader, List<Loader> parameter, Object parameter2) {
      parameter.add(loader);
      return null;
    }
  }
}
