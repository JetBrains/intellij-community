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

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import sun.misc.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class UrlClassLoader extends ClassLoader {
  private final ClassPath myClassPath;
  private final List<URL> myURLs;
  @NonNls static final String CLASS_EXTENSION = ".class";
  protected static final boolean myDebugTime = false;
  protected static final long NS_THRESHOLD = 10000000;

  public UrlClassLoader(ClassLoader parent) {
    this(Arrays.asList(((URLClassLoader)parent).getURLs()), parent.getParent(), true, true);
  }

  public UrlClassLoader(List<URL> urls, ClassLoader parent) {
    this(urls, parent, false, false);
  }

  public UrlClassLoader(URL[] urls, ClassLoader parent) {
    this(Arrays.asList(urls), parent, false, false);
  }

  public UrlClassLoader(List<URL> urls, ClassLoader parent, boolean canLockJars, boolean canUseCache) {
    super(parent);

    myClassPath = new ClassPath(urls.toArray(new URL[urls.size()]), canLockJars, canUseCache);
    myURLs = new ArrayList<URL>(urls);
  }

  public void addURL(URL url) {
    myClassPath.addURL(url);
    myURLs.add(url);
  }

  public void setAcceptUnescapedUrls(boolean acceptUnescapedUrls) {
    myClassPath.setAcceptUnescapedUrls(acceptUnescapedUrls);
  }

  public List<URL> getUrls() {
    return Collections.unmodifiableList(myURLs);
  }

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


  protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
    return super.loadClass(name, resolve);
  }

  @Nullable
  protected Class _findClass(final String name) {
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
      String pkgname = name.substring(0, i);
      // Check if package already loaded.
      Package pkg = getPackage(pkgname);
      if (pkg == null) {
        try {
          definePackage(pkgname, null, null, null, null, null, null, null);
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

  @Nullable
  public URL findResource(final String name) {
    final long started = myDebugTime ? System.nanoTime():0;

    try {
      return findResourceImpl(name);
    } finally {
      long doneFor = myDebugTime ? (System.nanoTime() - started):0;
      if (doneFor > NS_THRESHOLD) {
        System.out.println((doneFor / 1000000) + " ms for UrlClassLoader.getResource, resource:"+name);
      }
    }
  }

  protected URL findResourceImpl(final String name) {
    Resource res = _getResource(name);
    if (res == null) return null;
    return res.getURL();
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

  protected Enumeration<URL> findResources(String name) throws IOException {
    return myClassPath.getResources(name, true);
  }
}
