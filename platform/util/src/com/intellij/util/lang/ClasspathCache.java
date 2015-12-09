/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringHash;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.BloomFilterBase;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author max
 */
public class ClasspathCache {
  private final IntObjectHashMap myResourcePackagesCache = new IntObjectHashMap();
  private final IntObjectHashMap myClassPackagesCache = new IntObjectHashMap();

  private Map<String, Object> myResources2LoadersTempMap = new HashMap<String, Object>();
  private static final double PROBABILITY = 0.005d;
  private Name2LoaderFilter myNameFilter;

  static class LoaderData {
    private final List<String> myResourcePaths = new ArrayList<String>();
    private final List<String> myNames = new ArrayList<String>();

    public void addResourceEntry(String resourcePath) {
      myResourcePaths.add(resourcePath);
    }

    public void addNameEntry(String name) {
      myNames.add(transformName(name));
    }

    List<String> getResourcePaths() {
      return myResourcePaths;
    }
    List<String> getNames() {
      return myNames;
    }
  }

  private final ReadWriteLock myLock = new ReentrantReadWriteLock();

  public void applyLoaderData(LoaderData loaderData, Loader loader) {
    myLock.writeLock().lock();
    try {
      for(String resourceEntry:loaderData.myResourcePaths) {
        addResourceEntry(resourceEntry, loader);
      }
      for(String name:loaderData.myNames) {
        addNameEntry(name, loader);
      }
    } finally {
      myLock.writeLock().unlock();
    }
  }

  abstract static class LoaderIterator <ResultType, ParameterType, ParameterType2> {
    @Nullable
    abstract ResultType process(Loader loader, ParameterType parameter, ParameterType2 parameter2);
  }

  @Nullable <ResultType, ParameterType, ParameterType2> ResultType iterateLoaders(
    String resourcePath,
    LoaderIterator<ResultType, ParameterType, ParameterType2> iterator,
    ParameterType parameter,
    ParameterType2 parameter2) {
    myLock.readLock().lock();
    try {
      IntObjectHashMap map = resourcePath.endsWith(UrlClassLoader.CLASS_EXTENSION) ?
                                      myClassPackagesCache : myResourcePackagesCache;
      String packageName = getPackageName(resourcePath);

      int hash = packageName.hashCode();
      Object o = map.get(hash);

      if (o == null) return null;
      if (o instanceof Loader) return iterator.process((Loader)o, parameter, parameter2);
      Loader[] loaders = (Loader[])o;
      for(Loader l:loaders) {
        ResultType result = iterator.process(l, parameter, parameter2);
        if (result != null) return result;
      }
      return null;
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  private static String getPackageName(String resourcePath) {
    final int idx = resourcePath.lastIndexOf('/');
    return idx > 0 ? resourcePath.substring(0, idx) : "";
  }

  private void addResourceEntry(String resourcePath, Loader loader) {
    String packageName = getPackageName(resourcePath);
    IntObjectHashMap map = resourcePath.endsWith(UrlClassLoader.CLASS_EXTENSION) ?
                                    myClassPackagesCache : myResourcePackagesCache;
    int hash = packageName.hashCode();
    Object o = map.get(hash);
    if (o == null) map.put(hash, loader);
    else if (o instanceof Loader) {
      if (o != loader) map.put(hash, new Loader [] {(Loader)o, loader});
    } else {
      Loader[] loadersArray = (Loader[])o;
      for(Loader l:loadersArray) {
        if (l == loader) return;
      }
      map.put(hash, ArrayUtil.append(loadersArray, loader));
    }
  }

  private void addNameEntry(String name, Loader loader) {
    name = transformName(name);

    if (myNameFilter == null) {
      Object loaders = myResources2LoadersTempMap.get(name);
      if (loaders == null) {
        myResources2LoadersTempMap.put(name, loader);
      }
      else if (loaders instanceof Loader && loaders != loader) {
        myResources2LoadersTempMap.put(name, new Loader[] {(Loader)loaders, loader});
      } else if (loaders instanceof Loader[]) {
        boolean weHaveThisLoader = false;

        for(Loader existing:(Loader[])loaders) {
          if (existing == loader) {
            weHaveThisLoader = true;
            break;
          }
        }

        if (!weHaveThisLoader) {
          myResources2LoadersTempMap.put(name, ArrayUtil.append((Loader[])loaders, loader));
        }
      }
    } else {
      myNameFilter.add(name, loader);
    }
  }

  public boolean loaderHasName(String name, String shortName, Loader loader) {
    if (StringUtil.isEmpty(name)) return true;

    myLock.readLock().lock();

    try {
      boolean result;

      if (myNameFilter == null) {
        Object loaders = myResources2LoadersTempMap.get(shortName);
        result = contains(loader, loaders);
      }
      else {
        result = myNameFilter.maybeContains(shortName, loader);
      }

      return result;
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  private static boolean contains(Loader loader, Object loaders) {
    if (loaders == null) return false;

    boolean result = false;
    if (loaders == loader) {
      result = true;
    } else if (loaders instanceof Loader[]) {
      for(Loader existing:(Loader[])loaders) {
        if (existing == loader) {
          result = true;
          break;
        }
      }
    }
    return result;
  }

  static String transformName(String name) {
    name = StringUtil.trimEnd(name, "/");
    name = name.substring(name.lastIndexOf('/') + 1);

    if (name.endsWith(UrlClassLoader.CLASS_EXTENSION)) {
      String name1 = name;
      int $ = name1.indexOf('$');
      if ($ != -1) name1 = name1.substring(0, $);
      else {
        int index = name1.lastIndexOf('.');
        if (index >= 0) name1 = name1.substring(0, index);
      }
      name = name1;
    }
    return name;
  }

  void nameSymbolsLoaded() {
    myLock.writeLock().lock();

    try {
      if (myNameFilter != null) {
        return;
      }

      int nBits = 0;
      //noinspection UnusedDeclaration
      int uniques = 0;
      for(Map.Entry<String, Object> e: myResources2LoadersTempMap.entrySet()) {
        int size = e.getValue() instanceof Loader[] ? ((Loader[])e.getValue()).length : 1;
        if (size == 1) {
          ++uniques;
        }
        nBits += size;
      }
      if (nBits > 20000) {
        nBits += (int)(nBits * 0.03d); // allow some growth for Idea main loader
      }

      Name2LoaderFilter name2LoaderFilter = new Name2LoaderFilter(nBits, PROBABILITY);

      for(Map.Entry<String, Object> e: myResources2LoadersTempMap.entrySet()) {
        final String name = e.getKey();
        Object value = e.getValue();
        if (value instanceof Loader) {
          name2LoaderFilter.add(name, (Loader)value);
        } else {
          for (Loader loader : (Loader[])value) {
            name2LoaderFilter.add(name, loader);
          }
        }
      }

      myNameFilter = name2LoaderFilter;
      myResources2LoadersTempMap = null;
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  private static class Name2LoaderFilter extends BloomFilterBase {
    private static final int SEED = 31;

    Name2LoaderFilter(int nBits, double probability) {
      super(nBits, probability);
    }

    private boolean maybeContains(String name, Loader loader) {
      int hash = hashFromNameAndLoader(name, loader, StringHash.murmur(name, SEED));
      int hash2 = hashFromNameAndLoader(name, loader, hash);

      return maybeContains(hash, hash2);
    }

    private void add(String name, Loader loader) {
      int hash = hashFromNameAndLoader(name, loader, StringHash.murmur(name, SEED));
      int hash2 = hashFromNameAndLoader(name, loader, hash);

      addIt(hash, hash2);
    }

    private static int hashFromNameAndLoader(String name, Loader loader, int n) {
      int hash = StringHash.murmur(name, n);
      int i = loader.getIndex();
      while (i > 0) {
        hash = hash * n + ((i % 10) + '0');
        i /= 10;
      }
      return hash;
    }
  }
}