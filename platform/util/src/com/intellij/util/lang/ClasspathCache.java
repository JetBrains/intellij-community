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

import com.intellij.util.SmartList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;

import java.util.List;

public class ClasspathCache {
  private final TIntObjectHashMap<List<Loader>> myClassPackagesCache = new TIntObjectHashMap<List<Loader>>();
  private final TIntObjectHashMap<List<Loader>> myResourcePackagesCache = new TIntObjectHashMap<List<Loader>>();
  private final TIntHashSet myResourceIndex = new TIntHashSet();

  public void addResourceEntry(String resourcePath, Loader loader) {
    final List<Loader> loaders = getLoaders(resourcePath);
    if (!loaders.contains(loader)) { // TODO Make linked hash set instead?
      loaders.add(loader);
    }
  }

  public List<Loader> getLoaders(String resourcePath) {
    boolean isClassFile = resourcePath.endsWith(UrlClassLoader.CLASS_EXTENSION);
    final int idx = resourcePath.lastIndexOf('/');
    String packageName = idx > 0 ? resourcePath.substring(0, idx) : "";

    TIntObjectHashMap<List<Loader>> map = isClassFile ? myClassPackagesCache : myResourcePackagesCache;
    int hash = packageName.hashCode();
    List<Loader> list = map.get(hash);
    if (list == null) {
      list = new SmartList<Loader>();
      map.put(hash, list);
    }

    return list;
  }

  public void addNameEntry(String name, Loader loader) {
    int hash = hashFromNameAndLoader(transformName(name), loader);
    myResourceIndex.add(hash);
  }

  public boolean loaderHasName(String name, Loader loader) {
    int hash = hashFromNameAndLoader(transformName(name), loader);

    boolean result = myResourceIndex.contains(hash);
    ++requests;

    if (!result) ++hits;

    if (requests % 1000 == 0 && UrlClassLoader.doDebug && false) {
      UrlClassLoader.debug("Avoided disk hits: "+hits + " from " + requests);
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

  private static int hits, requests;

  private int hashFromNameAndLoader(String name, Loader loader) {
    int hash = name.hashCode();
    int i = loader.getIndex();
    while(i > 0) {
      hash = hash * 31 + ((i % 10) + '0');
      i /= 10;
    }
    return hash;
  }
}
