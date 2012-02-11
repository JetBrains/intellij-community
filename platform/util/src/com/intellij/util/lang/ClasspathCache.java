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

/*
 * @author max
 */
package com.intellij.util.lang;

import com.intellij.openapi.util.text.StringHash;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.Nullable;
import sun.misc.Resource;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClasspathCache {
  private static final boolean doDebug = false;
  private final DebugInfo myDebugInfo;

  private final TIntObjectHashMap<Object> myResourcePackagesCache = new TIntObjectHashMap<Object>();
  private final TIntObjectHashMap<Object> myClassPackagesCache = new TIntObjectHashMap<Object>();

  private THashMap<String, Set<Loader>> myResources2LoadersTempMap = new THashMap<String, Set<Loader>>();
  private static final double PROBABILITY = 0.005d;
  private BloomFilter myNameFilter;
  private boolean myTempMapMode = true;

  public ClasspathCache() {
    if(doDebug) {
      myDebugInfo = new DebugInfo();
    } else {
      myDebugInfo = null;
    }
  }

  public void addResourceEntry(String resourcePath, Loader loader) {
    if (doDebug) myDebugInfo.addResourceEntry(resourcePath, loader);

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

  static abstract class LoaderIterator <ResultType, ParameterType, ParameterType2> {
    abstract @Nullable ResultType process(Loader loader, ParameterType parameter, ParameterType2 parameter2);
  }

  @Nullable <ResultType, ParameterType, ParameterType2> ResultType iterateLoaders(
    String resourcePath,
    LoaderIterator<ResultType, ParameterType, ParameterType2> iterator,
    ParameterType parameter,
    ParameterType2 parameter2) {
    TIntObjectHashMap<Object> map = resourcePath.endsWith(UrlClassLoader.CLASS_EXTENSION) ?
                                    myClassPackagesCache : myResourcePackagesCache;    
    String packageName = getPackageName(resourcePath);
    
    int hash = packageName.hashCode();
    Object o = map.get(hash);
    if (doDebug) myDebugInfo.checkLoadersCount(resourcePath, o);    
      
    if (o == null) return null;
    if (o instanceof Loader) return iterator.process((Loader)o, parameter, parameter2);
    Loader[] loaders = (Loader[])o;
    for(Loader l:loaders) {
      ResultType result = iterator.process(l, parameter, parameter2);
      if (result != null) return result;
    }
    return null;
  }

  private static String getPackageName(String resourcePath) {
    final int idx = resourcePath.lastIndexOf('/');
    return idx > 0 ? resourcePath.substring(0, idx) : "";
  }

  private int registeredBeforeClose, registeredAfterClose;

  public void addNameEntry(String name, Loader loader) {
    name = transformName(name);
    if (doDebug) myDebugInfo.addNameEntry(name, loader);
    if (myTempMapMode) {
      Set<Loader> loaders = myResources2LoadersTempMap.get(name);
      if (loaders == null) myResources2LoadersTempMap.put(name, loaders = new THashSet<Loader>());
      boolean added = loaders.add(loader);
      if (UrlClassLoader.doDebug && added) ++registeredBeforeClose;
    } else {
      if (UrlClassLoader.doDebug) {
        if (!myNameFilter.maybeContains(name, loader)) ++registeredAfterClose;
      }

      myNameFilter.add(name, loader);
    }
  }

  public boolean loaderHasName(String name, Loader loader) {
    String origName = name;
    boolean result;
    name = transformName(name);
    
    if (myTempMapMode) {
      ++requests;
      Set<Loader> loaders = myResources2LoadersTempMap.get(name);
      result = loaders != null && loaders.contains(loader);

      if (!result) ++hits;

      if (doDebug) {
        boolean result2 = myDebugInfo.loaderHashName(name, loader);
        if (result2 != result) {
          ++diffs3;
        }
        Resource resource = loader.getResource(origName, true);
        if ((resource != null && !result) || (resource == null && result)) {
          ++falseHits;
        }
      }

      if (requests % 1000 == 0 && UrlClassLoader.doDebug) {
        UrlClassLoader.debug("Avoided disk hits: "+hits + " from " + requests + (doDebug ? ", false hits:" + falseHits + ", bitmap diffs:"+diffs3:""));
      }
    } else {
      ++requests2;
      result = myNameFilter.maybeContains(name, loader);
      if (!result) ++hits2;

      if (doDebug) {
        boolean result2 = myDebugInfo.loaderHashName(name, loader);
        if (result2 != result) {
          ++diffs2;
        }

        Set<Loader> loaders = myResources2LoadersTempMap.get(name);
        if (result != (loaders != null && loaders.contains(loader))) {
          ++diffs;
        }

        Resource resource = loader.getResource(origName, true);
        if (resource == null && result) {
          ++falseHits2;
        }
        if (resource != null && !result) {
          ++falseHits2;
        }
      }

      if (requests2 % 1000 == 0 && UrlClassLoader.doDebug) {
        UrlClassLoader.debug("Avoided disk hits2: "+hits2 + " from " + requests2 + (doDebug ? "," + diffs + ", false hits:" + falseHits2 + ", bitmap diffs:"+diffs2:""));
      }
    }

    return result;
  }
  
  private String transformName(String name) {
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

  private static int hits, requests, falseHits, requests2, hits2, falseHits2, diffs, diffs2, diffs3;

  void nameSymbolsLoaded() {
    if (!myTempMapMode) {
      if (UrlClassLoader.doDebug && registeredAfterClose > 0) {
        UrlClassLoader.debug("Registered number of classes after close "+registeredAfterClose + " "+toString());
      }
      return;
    }

    if (UrlClassLoader.doDebug) {
      UrlClassLoader.debug("Registered number of classes before classes "+registeredBeforeClose + " "+toString());
    }

    myTempMapMode = false;

    int nBits = 0, uniques = 0;
    for(Map.Entry<String, Set<Loader>> e:myResources2LoadersTempMap.entrySet()) {
      int size = e.getValue().size();
      if (size == 1) {
        ++uniques;
      }
      nBits += size;
    }
    if (nBits > 20000) {
      nBits += (int)(nBits * 0.03d); // allow some growth for Idea main loader
    }

    myNameFilter = new BloomFilter(nBits, PROBABILITY);

    for(Map.Entry<String, Set<Loader>> e:myResources2LoadersTempMap.entrySet()) {
      final String name = e.getKey();
      for(Loader loader: e.getValue()) {
        myNameFilter.add(name, loader);
      }
    }

    if (!doDebug) {
      myResources2LoadersTempMap = null;
    }
  }

  static class BloomFilter {
    private final int myHashFunctionCount;
    private final int NBITS;
    private final BitSet myResourceMap;
    private static final int SEED = 31;

    BloomFilter(int nBits, double probability) {
      int bitsPerNameFactor = (int)Math.ceil(-Math.log(probability) / (Math.log(2) * Math.log(2)));
      myHashFunctionCount = (int)Math.ceil(bitsPerNameFactor * Math.log(2));

      nBits = nBits * bitsPerNameFactor;

      if ((nBits & 1) == 0) ++nBits;
      while(!isPrime(nBits))  nBits += 2;
      NBITS = nBits;
      myResourceMap = new BitSet(NBITS);
    }

    private static boolean isPrime(int bits) {
      if ((bits & 1) == 0) return false;
      int sqrt = (int)Math.sqrt(bits);
      for(int i = 3; i <= sqrt; i+=2) {
        if (bits % i == 0) return false;
      }
      return true;
    }

    private boolean maybeContains(String name, Loader loader) {
      int hash = hashFromNameAndLoader(name, loader, StringHash.murmur(name, SEED));
      int hash2 = hashFromNameAndLoader(name, loader, hash);

      for (int i = 0; i < myHashFunctionCount; ++i) {
        if (!myResourceMap.get(Math.abs((hash + i * hash2) % NBITS))) return false;
      }
      return true;
    }

    public void add(String name, Loader loader) {
      int hash1 = hashFromNameAndLoader(name, loader, StringHash.murmur(name, SEED));
      int hash2 = hashFromNameAndLoader(name, loader, hash1);

      for (int i = 0; i < myHashFunctionCount; ++i) {
        myResourceMap.set(Math.abs((hash1 + i * hash2) % NBITS));
      }
    }

    private int hashFromNameAndLoader(String name, Loader loader, int n) {
      int hash = StringHash.murmur(name, n);
      int i = loader.getIndex();
      while (i > 0) {
        hash = hash * n + ((i % 10) + '0');
        i /= 10;
      }
      return hash;
    }
  }

  static class DebugInfo {
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

    private void addResourceEntry(String resourcePath, Loader loader) {
      final List<Loader> loaders = getLoaders(resourcePath);
      if (!loaders.contains(loader)) { // TODO Make linked hash set instead?
        loaders.add(loader);
      }
    }

    private void addNameEntry(String name, Loader loader) {
      int hash = hashFromNameAndLoader(name, loader);
      myResourceIndex.add(hash);
    }

    private int hashFromNameAndLoader(String name, Loader loader) {
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
      if (o == null && loaders1.size() != 0 ||
          o instanceof Loader && loaders1.size() != 1 ||
          o instanceof Loader[] && loaders1.size() != ((Loader[])o).length
        ) {
        assert false;
      }
    }

    private boolean loaderHashName(String name, Loader loader) {
      return myResourceIndex.contains(hashFromNameAndLoader(name, loader));
    }
  }
}
