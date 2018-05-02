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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;

/**
 * An object responsible for loading classes and resources from a particular classpath element: a jar or a directory.
 * 
 * @see JarLoader
 * @see FileLoader
 */
abstract class Loader {
  private final URL myURL;
  private final int myIndex;

  Loader(URL url, int index) {
    myURL = url;
    myIndex = index;
  }

  URL getBaseURL() {
    return myURL;
  }

  @Nullable
  abstract Resource getResource(String name);
  
  @NotNull abstract ClasspathCache.LoaderData buildData() throws IOException;

  int getIndex() {
    return myIndex;
  }
}
