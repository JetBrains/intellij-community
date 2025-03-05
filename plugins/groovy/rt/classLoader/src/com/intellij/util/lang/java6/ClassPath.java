// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang.java6;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.openapi.diagnostic.LoggerRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class ClassPath {
  private static final ResourceStringLoaderIterator ourResourceIterator = new ResourceStringLoaderIterator();
  private static final LoaderCollector ourLoaderCollector = new LoaderCollector();
  public static final String CLASSPATH_JAR_FILE_NAME_PREFIX = "classpath";

  static final boolean recordLoadingInfo = Boolean.getBoolean("idea.log.classpath.info");
  static final boolean recordLoadingStats = recordLoadingInfo || Boolean.getBoolean("idea.record.classloading.stats");

  private static final Set<String> loadedClassNames;
  private static final AtomicLong ourTotalTime = new AtomicLong();
  private static final AtomicInteger ourTotalRequests = new AtomicInteger();
  private static final ThreadLocal<Boolean> doingTiming = new ThreadLocal<>();

  private final List<URL> myUrls = new ArrayList<>();
  private final List<Loader> myLoaders = new ArrayList<>();

  private volatile boolean myAllUrlsWereProcessed;

  private final AtomicInteger myLastLoaderProcessed = new AtomicInteger();
  private final Map<URL, Loader> myLoadersMap = new HashMap<>();
  private final ClasspathCache myCache = new ClasspathCache();
  private final Set<URL> myURLsWithProtectionDomain;

  final boolean myCanLockJars; // true implies that the .jar file will not be modified in the lifetime of the JarLoader
  private final boolean myCanUseCache;
  private final boolean myAcceptUnescapedUrls;
  final boolean myCanHavePersistentIndex;
  final boolean myLazyClassloadingCaches;
  final boolean myLogErrorOnMissingJar;

  static {
    loadedClassNames = recordLoadingInfo ? Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>()) : null;
    if (recordLoadingInfo) {
      Runtime.getRuntime().addShutdownHook(new Thread("Shutdown hook for tracing classloading information") {
        @Override
        public void run() {
          //noinspection UseOfSystemOutOrSystemErr
          System.out.println("Classloading requests: " + ClassPath.class.getClassLoader() + "," +
                             ourTotalRequests + ", time:" + (ourTotalTime.get() / 1000000) + "ms");
        }
      });
    }
  }

  public ClassPath(List<URL> urls,
                   boolean canLockJars,
                   boolean canUseCache,
                   boolean canHavePersistentIndex,
                   boolean logErrorOnMissingJar,
                   boolean lazyClassloadingCaches,
                   @NotNull Set<URL> urlsWithProtectionDomain) {
    myLazyClassloadingCaches = lazyClassloadingCaches;
    myCanLockJars = canLockJars;
    myCanUseCache = canUseCache && !myLazyClassloadingCaches;
    myAcceptUnescapedUrls = false;
    myCanHavePersistentIndex = canHavePersistentIndex;
    myLogErrorOnMissingJar = logErrorOnMissingJar;
    myURLsWithProtectionDomain = urlsWithProtectionDomain;
    push(urls);
  }

  // in nanoseconds
  public static long getTotalTime() {
    return ourTotalTime.get();
  }

  public static long getTotalRequests() {
    return ourTotalRequests.get();
  }

  /** @deprecated adding URLs to classpath at runtime could lead to hard-to-debug errors */
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  void addURL(URL url) {
    push(Collections.singletonList(url));
  }

  private void push(List<URL> urls) {
    if (!urls.isEmpty()) {
      synchronized (myUrls) {
        for (int i = urls.size() - 1; i >= 0; i--) {
          myUrls.add(urls.get(i));
        }
        myAllUrlsWereProcessed = false;
      }
    }
  }

  @Nullable
  public Resource getResource(@NotNull String s) {
    final long started = startTiming();
    Resource resource = null;
    try {
      String shortName = ClasspathCache.transformName(s);

      int i;
      if (myCanUseCache) {
        boolean allUrlsWereProcessed = myAllUrlsWereProcessed;
        i = allUrlsWereProcessed ? 0 : myLastLoaderProcessed.get();

        resource = myCache.iterateLoaders(s, ourResourceIterator, s, this, shortName);
        if (resource != null || allUrlsWereProcessed) {
          return resource;
        }
      }
      else {
        i = 0;
      }

      Loader loader;
      while ((loader = getLoader(i++)) != null) {
        if (myCanUseCache && !loader.containsName(s, shortName)) {
          continue;
        }
        resource = loader.getResource(s);
        if (resource != null) {
          return resource;
        }
      }
    }
    finally {
      logInfo(this, started, s, resource);
    }

    return null;
  }

  public Enumeration<URL> getResources(final String name) {
    return new MyEnumeration(name);
  }

  @Nullable
  private Loader getLoader(int i) {
    if (i < myLastLoaderProcessed.get()) { // volatile read
      return myLoaders.get(i);
    }

    return getLoaderSlowPath(i);
  }

  @Nullable
  private synchronized Loader getLoaderSlowPath(int i) {
    while (myLoaders.size() < i + 1) {
      URL url;
      synchronized (myUrls) {
        int size = myUrls.size();
        if (size == 0) {
          if (myCanUseCache) {
            myAllUrlsWereProcessed = true;
          }
          return null;
        }
        url = myUrls.remove(size - 1);
      }

      if (myLoadersMap.containsKey(url)) {
        continue;
      }

      try {
        initLoaders(url);
      }
      catch (IOException e) {
        LoggerRt.getInstance(ClassPath.class).info("url: " + url, e);
      }
    }

    return myLoaders.get(i);
  }

  @ReviseWhenPortedToJDK(value = "7", description = "use URL -> URI -> Path conversion")
  private void initLoaders(@NotNull URL url) throws IOException {
    String path;

    if (myAcceptUnescapedUrls) {
      path = url.getFile();
    }
    else {
      try {
        path = url.toURI().getSchemeSpecificPart();
      }
      catch (URISyntaxException e) {
        LoggerRt.getInstance(ClassPath.class).error("url: " + url, e);
        path = url.getFile();
      }
    }

    if (path != null && "file".equals(url.getProtocol())) {
      File file = new File(path);
      Loader loader = createLoader(url, file, file.getName().startsWith(CLASSPATH_JAR_FILE_NAME_PREFIX));
      if (loader != null) {
        initLoader(url, loader);
      }
    }
  }

  private Loader createLoader(@NotNull URL url, @NotNull File file, boolean processRecursively) throws IOException {
    if (file.isDirectory()) {
      return new FileLoader(url, this);
    }
    if (!file.isFile()) {
      return null;
    }

    boolean isSigned = myURLsWithProtectionDomain.contains(url);
    JarLoader loader = isSigned ? new SecureJarLoader(url, file.getPath(), this) : new JarLoader(url, file.getPath(), this);
    if (processRecursively) {
      String[] referencedJars = loadManifestClasspath(loader);
      if (referencedJars != null) {
        long s2 = recordLoadingInfo ? System.nanoTime() : 0;
        List<URL> urls = new ArrayList<>(referencedJars.length);
        for (String referencedJar : referencedJars) {
          try {
            urls.add(new URI(referencedJar).toURL());
          }
          catch (Exception e) {
            LoggerRt.getInstance(ClassPath.class).warn("url: " + url + " / " + referencedJar, e);
          }
        }
        push(urls);
        if (recordLoadingInfo) {
          //noinspection UseOfSystemOutOrSystemErr
          System.out.println("Loaded all " + referencedJars.length + " urls " + (System.nanoTime() - s2) / 1000000 + "ms");
        }
      }
    }
    return loader;
  }

  private void initLoader(@NotNull URL url, @NotNull Loader loader) throws IOException {
    if (myCanUseCache) {
      ClasspathCache.LoaderData data = loader.buildData();
      myCache.applyLoaderData(data, loader);

      boolean lastOne;
      synchronized (myUrls) {
        lastOne = myUrls.isEmpty();
      }

      if (lastOne) {
        myAllUrlsWereProcessed = true;
      }
    }
    myLoaders.add(loader);
    myLoadersMap.put(url, loader);
    myLastLoaderProcessed.incrementAndGet(); // volatile write
  }

  private final class MyEnumeration implements Enumeration<URL> {
    private int myIndex;
    private Resource myRes;
    @NotNull
    private final String myName;
    private final String myShortName;
    private final List<Loader> myLoaders;

    MyEnumeration(@NotNull String name) {
      myName = name;
      myShortName = ClasspathCache.transformName(name);
      List<Loader> loaders = null;

      if (myCanUseCache && myAllUrlsWereProcessed) {
        Collection<Loader> loadersSet = new LinkedHashSet<>();
        myCache.iterateLoaders(name, ourLoaderCollector, loadersSet, this, myShortName);

        if (name.endsWith("/")) {
          myCache.iterateLoaders(name.substring(0, name.length() - 1), ourLoaderCollector, loadersSet, this, myShortName);
        }
        else {
          myCache.iterateLoaders(name + "/", ourLoaderCollector, loadersSet, this, myShortName);
        }

        loaders = new ArrayList<>(loadersSet);
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
            if (!loader.containsName(myName, myShortName)) {
              myRes = null;
              continue;
            }
            myRes = loader.getResource(myName);
            if (myRes != null) return true;
          }
        }
        else {
          while ((loader = getLoader(myIndex++)) != null) {
            if (myCanUseCache && !loader.containsName(myName, myShortName)) continue;
            myRes = loader.getResource(myName);
            if (myRes != null) return true;
          }
        }
      }
      finally {
        logInfo(ClassPath.this, started, myName, null);
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

  private static final class ResourceStringLoaderIterator extends ClasspathCache.LoaderIterator<Resource, String, ClassPath> {
    @Override
    Resource process(@NotNull Loader loader, @NotNull String s, @NotNull ClassPath classPath, @NotNull String shortName) {
      return loader.containsName(s, shortName) ? findInLoader(loader, s) : null;
    }

    @Nullable
    private static Resource findInLoader(@NotNull Loader loader, @NotNull String s) {
      return loader.getResource(s);
    }
  }

  private static class LoaderCollector extends ClasspathCache.LoaderIterator<Object, Collection<Loader>, Object> {
    @Override
    Object process(@NotNull Loader loader, @NotNull Collection<Loader> parameter, @NotNull Object parameter2, @NotNull String shortName) {
      parameter.add(loader);
      return null;
    }
  }

  private static long startTiming() {
    if (!recordLoadingStats || doingTiming.get() != null) {
      return 0;
    }

    doingTiming.set(Boolean.TRUE);
    return System.nanoTime();
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void logInfo(ClassPath path, long started, String resourceName, Resource resource) {
    if (resource != null && recordLoadingInfo) {
      loadedClassNames.add(resource.getURL().getPath());
    }

    if (started == 0 || !recordLoadingStats) {
      return;
    }

    doingTiming.remove();

    long time = System.nanoTime() - started;
    long totalTime = ourTotalTime.addAndGet(time);
    int totalRequests = ourTotalRequests.incrementAndGet();
    if (recordLoadingInfo) {
      if (time > 3000000L) {
        System.out.println(time / 1000000 + " ms for " + resourceName);
      }
      if (totalRequests % 10000 == 0) {
        System.out.println(path.getClass().getClassLoader() + ", requests:" + ourTotalRequests + ", time:" + (totalTime / 1000000) + "ms");
      }
    }
  }

  private static String[] loadManifestClasspath(JarLoader loader) {
    try {
      String classPath = loader.getClassPathManifestAttribute();
      if (classPath != null) {
        String[] urls = classPath.split(" ");
        if (urls.length > 0 && urls[0].startsWith("file:")) {
          return urls;
        }
      }
    }
    catch (Exception ignore) {
    }
    return null;
  }
}