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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringHash;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.BloomFilterBase;
import com.intellij.util.SmartList;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.Nullable;
import sun.misc.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author max
 */
public class ClasspathCache {
  static final Logger LOG = Logger.getInstance(ClasspathCache.class);
  static final boolean doDebug = LOG.isDebugEnabled();

  private final DebugInfo myDebugInfo;

  private final TIntObjectHashMap<Object> myResourcePackagesCache = new TIntObjectHashMap<Object>();
  private final TIntObjectHashMap<Object> myClassPackagesCache = new TIntObjectHashMap<Object>();

  private THashMap<String, Object> myResources2LoadersTempMap = new THashMap<String, Object>();
  private static final double PROBABILITY = 0.005d;
  private Name2LoaderFilter myNameFilter;

  public ClasspathCache() {
    myDebugInfo = doDebug ? new DebugInfo() : new NullDebugInfo();
  }

  public static class LoaderData {
    private final List<String> myResourcePaths = new ArrayList<String>();
    private final List<String> myNames = new ArrayList<String>();
    private final Loader myLoader;

    public LoaderData(Loader loader) {
      myLoader = loader;
    }

    public void addResourceEntry(String resourcePath) {
      myResourcePaths.add(resourcePath);
    }

    public void addNameEntry(String name) {
      myNames.add(transformName(name));
    }
  }

  private final ReadWriteLock myLock = new ReentrantReadWriteLock();

  public void applyLoaderData(LoaderData loaderData) {
    myLock.writeLock().lock();
    try {
      for(String resourceEntry:loaderData.myResourcePaths) {
        addResourceEntry(resourceEntry, loaderData.myLoader);
      }
      for(String name:loaderData.myNames) {
        addNameEntry(name, loaderData.myLoader);
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
      TIntObjectHashMap<Object> map = resourcePath.endsWith(UrlClassLoader.CLASS_EXTENSION) ?
                                      myClassPackagesCache : myResourcePackagesCache;
      String packageName = getPackageName(resourcePath);

      int hash = packageName.hashCode();
      Object o = map.get(hash);
      myDebugInfo.checkLoadersCount(resourcePath, o);

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
    myDebugInfo.addResourceEntry(resourcePath, loader);

    String packageName = getPackageName(resourcePath);
    TIntObjectHashMap<Object> map = resourcePath.endsWith(UrlClassLoader.CLASS_EXTENSION) ?
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
    myDebugInfo.addNameEntry(name, loader);

    if (myNameFilter == null) {
      Object loaders = myResources2LoadersTempMap.get(name);
      boolean added = false;
      if (loaders == null) {
        myResources2LoadersTempMap.put(name, loader);
        added = true;
      }
      else if (loaders instanceof Loader && loaders != loader) {
        myResources2LoadersTempMap.put(name, new Loader[] {(Loader)loaders, loader});
        added = true;
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
          added = true;
        }
      }

      if (doDebug && added) ++registeredBeforeClose;
    } else {
      if (doDebug) {
        if (!myNameFilter.maybeContains(name, loader)) ++registeredAfterClose;
      }

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

        if (doDebug) {
          ++requestsWithoutNameFilter;
          if (!result) ++hits;
          boolean result2 = myDebugInfo.loaderHasName(shortName, loader);
          if (result2 != result) {
            ++diffs3;
          }
          Resource resource = loader.getResource(name, true);
          if (resource != null && !result || resource == null && result) {
            ++falseHits;
          }

          if (requestsWithoutNameFilter % 1000 == 0) {
            LOG.debug("Avoided disk hits: " + hits + " from " + requestsWithoutNameFilter + ", false hits:" + falseHits + ", bitmap diffs:" + diffs3);
          }
        }
      }
      else {
        result = myNameFilter.maybeContains(shortName, loader);

        if (doDebug) {
          ++requestsWithNameFilter;
          if (!result) ++avoidedDiskHits2;
          boolean result2 = myDebugInfo.loaderHasName(shortName, loader);
          if (result2 != result) {
            ++diffs2;
          }

          Object loaders = myResources2LoadersTempMap.get(shortName);
          if (result != contains(loader, loaders)) {
            ++diffs;
          }

          Resource resource = loader.getResource(name, true);
          if (resource == null && result) {
            ++falseHits2;
          }
          if (resource != null && !result) {
            ++falseHits2;
          }

          if (requestsWithNameFilter % 1000 == 0) {
            LOG.debug("Avoided disk hits2: " + avoidedDiskHits2 + " from " +
                      requestsWithNameFilter + "," + diffs + ", false hits:" + falseHits2 + ", bitmap diffs:" + diffs2);
          }
        }
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
    if (name.endsWith("/")) {
      name = name.substring(0, name.length() - 1);
    }
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

  private int registeredBeforeClose;
  private int registeredAfterClose;
  private int hits;
  private int requestsWithoutNameFilter;
  private int falseHits;
  private int requestsWithNameFilter;
  private int avoidedDiskHits2;
  private int falseHits2;
  private int diffs;
  private int diffs2;
  private int diffs3;

  void nameSymbolsLoaded() {
    myLock.writeLock().lock();

    try {
      if (myNameFilter != null) {
        if (doDebug && registeredAfterClose > 0) {
          LOG.debug("Registered number of classes after close " + registeredAfterClose + " " + toString());
        }
        return;
      }

      if (doDebug) {
        LOG.debug("Registered number of classes before classes " + registeredBeforeClose + " " + toString());
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
      if (!doDebug) {
        myResources2LoadersTempMap = null;
      }
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

  private static class DebugInfo {
    private final HashMap<String, List<Loader>> myClassPackagesCache = new HashMap<String, List<Loader>>();
    private final HashMap<String, List<Loader>> myResourcePackagesCache = new HashMap<String, List<Loader>>();
    private final TIntHashSet myResourceIndex = new TIntHashSet();

    private List<Loader> getLoaders(String resourcePath) {
      boolean isClassFile = resourcePath.endsWith(UrlClassLoader.CLASS_EXTENSION);
      final int idx = resourcePath.lastIndexOf('/');
      String packageName = idx > 0 ? resourcePath.substring(0, idx) : "";

      Map<String, List<Loader>> map = isClassFile ? myClassPackagesCache : myResourcePackagesCache;
      List<Loader> list = map.get(packageName);
      if (list == null) {
        list = new SmartList<Loader>();
        map.put(packageName, list);
      }

      return list;
    }

    protected void addResourceEntry(String resourcePath, Loader loader) {
      final List<Loader> loaders = getLoaders(resourcePath);
      if (!loaders.contains(loader)) { // TODO Make linked hash set instead?
        loaders.add(loader);
      }
    }

    protected void addNameEntry(String name, Loader loader) {
      int hash = hashFromNameAndLoader(name, loader);
      myResourceIndex.add(hash);
    }

    protected static int hashFromNameAndLoader(String name, Loader loader) {
      int hash = name.hashCode();
      int i = loader.getIndex();
      while(i > 0) {
        hash = hash * 31 + ((i % 10) + '0');
        i /= 10;
      }
      return hash;
    }

    public void checkLoadersCount(String resourcePath, Object o) {
      List<Loader> loaders1 = getLoaders(resourcePath);
      if (o == null && !loaders1.isEmpty() ||
          o instanceof Loader && loaders1.size() != 1 ||
          o instanceof Loader[] && loaders1.size() != ((Loader[])o).length
        ) {
        assert false;
      }
    }

    protected boolean loaderHasName(String name, Loader loader) {
      return myResourceIndex.contains(hashFromNameAndLoader(name, loader));
    }
  }

  private static class NullDebugInfo extends DebugInfo {
    @Override
    public void checkLoadersCount(String resourcePath, Object o) {
    }

    @Override
    protected void addResourceEntry(String resourcePath, Loader loader) {
    }

    @Override
    protected void addNameEntry(String name, Loader loader) {
    }

    @Override
    protected boolean loaderHasName(String name, Loader loader) {
      return false;
    }
  }
}