/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.NoSuchElementException;

class ClassPath {
  private final Stack<URL> myUrls = new Stack<URL>();
  private final ArrayList<Loader> myLoaders = new ArrayList<Loader>();
  private final HashMap<URL,Loader> myLoadersMap = new HashMap<URL, Loader>();
  private final ClasspathCache myCache = new ClasspathCache();

  @NonNls private static final String FILE_PROTOCOL = "file";
  private static final boolean myDebugTime = false;
  private final boolean myCanLockJars;
  private final boolean myCanUseCache;
  private static final long NS_THRESHOLD = 10000000L;

  private static PrintStream ourOrder;

  @SuppressWarnings({"UnusedDeclaration"})
  private static void printOrder(Loader loader, String resource) {
    if (ourOrder == null) {
      final File orderFile = new File(PathManager.getBinPath() + File.separator + "order.txt");
      try {
        if (!FileUtil.ensureCanCreateFile(orderFile)) return;
        ourOrder = new PrintStream(new FileOutputStream(orderFile, true));
        ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
          public void run() {
            ourOrder.close();
          }
        });
      }
      catch (IOException e) {
        return;
      }
    }

    if (ourOrder != null) {
      String jarURL = FileUtil.toSystemIndependentName(loader.getBaseURL().getFile());
      jarURL = jarURL.replaceFirst(FileUtil.toSystemIndependentName(PathManager.getHomePath()), "");
      jarURL = StringUtil.trimEnd(StringUtil.trimStart(jarURL, "file:/"), "!/");
      ourOrder.println(resource + ":" + jarURL);
    }
  }

  public ClassPath(URL[] urls, boolean canLockJars, boolean canUseCache) {
    myCanLockJars = canLockJars;
    myCanUseCache = canUseCache;
    push(urls);
  }

  void addURL(URL url) {
    push(new URL[]{url});
  }

  @Nullable
  public Resource getResource(String s, boolean flag) {
    final long started = myDebugTime ? System.nanoTime():0;

    try {
      int i;
      if (myCanUseCache) {
        final List<Loader> loaders = myCache.getLoaders(s);
        for (Loader loader : loaders) {
          final Resource resource = loader.getResource(s, flag);
          if (resource != null) {
            //printOrder(loader, s);
            return resource;
          }
        }

        synchronized (myUrls) {
          if (myUrls.isEmpty()) return null;
        }

        i = myLoaders.size();
      }
      else {
        i = 0;
      }

      for (Loader loader; (loader = getLoader(i)) != null; i++) {
        Resource resource = loader.getResource(s, flag);
        if (resource != null) {
          return resource;
        }
      }

      return null;
    }
    finally {
      long doneFor = myDebugTime ? System.nanoTime() - started:0;
      if (doneFor > NS_THRESHOLD) {
        System.out.println((doneFor/1000000) + " ms for getResource:"+s+", flag:"+flag);
      }
    }
  }

  public Enumeration<URL> getResources(final String name, final boolean check) {
    return new MyEnumeration(name, check);
  }

  @Nullable
  private synchronized Loader getLoader(int i) {
    while (myLoaders.size() < i + 1) {
      URL url;
      synchronized (myUrls) {
        if (myUrls.empty()) return null;
        url = myUrls.pop();
      }

      if (myLoadersMap.containsKey(url)) continue;

      Loader loader;
      try {
        loader = getLoader(url);
        if (loader == null) continue;
      }
      catch (IOException ioexception) {
        continue;
      }

      myLoaders.add(loader);
      myLoadersMap.put(url, loader);
    }

    return myLoaders.get(i);
  }

  @Nullable
  private Loader getLoader(final URL url) throws IOException {
    String s;
    try {
      s = url.toURI().getSchemeSpecificPart();
    } catch (URISyntaxException e) {
      e.printStackTrace();
      s = url.getFile();
    }

    Loader loader = null;
    if (s != null  && new File(s).isDirectory()) {
      if (FILE_PROTOCOL.equals(url.getProtocol())) {
        loader = new FileLoader(url);
      }
    }
    else {
      loader = new JarLoader(url, myCanLockJars);
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

  private void push(URL[] urls) {
    synchronized (myUrls) {
      for (int i = urls.length - 1; i >= 0; i--) myUrls.push(urls[i]);

    }
  }

  private class MyEnumeration implements Enumeration<URL> {
    private int myIndex = 0;
    private Resource myRes = null;
    private final String myName;
    private final boolean myCheck;

    public MyEnumeration(String name, boolean check) {
      myName = name;
      myCheck = check;
    }

    private boolean next() {
      if (myRes != null) return true;

      Loader loader;
      while ((loader = getLoader(myIndex++)) != null) {
        myRes = loader.getResource(myName, myCheck);
        if (myRes != null) return true;
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
}
