/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.util.ArrayList;
import java.util.List;

/**
 * Holds resource and class names that are present inside a jar or a directory represented by a particular {@link Loader} instance.
 * This data is then used by {@link UrlClassLoader} to avoid looking inside particular loaders when searching for classes/resources, and thus
 * to speed up the operation.<p/>
 * 
 * @see LoaderIndexProvider
 * 
 * @author peter
*/
public class ClasspathLoaderIndex {
  private final List<String> myResourcePaths = new ArrayList<String>();
  private final List<String> myNames = new ArrayList<String>();

  public void addResourceEntry(String resourcePath) {
    myResourcePaths.add(resourcePath);
  }

  public void addNameEntry(String name) {
    myNames.add(ClasspathCache.transformName(name));
  }

  public List<String> getResourcePaths() {
    return myResourcePaths;
  }

  public List<String> getNames() {
    return myNames;
  }
}
