/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.util.io.FileUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class ExternalClasspathClassLoader extends URLClassLoader {
  public ExternalClasspathClassLoader(ClassLoader parent) {
    super(parseUrls(), parent);
  }

  public ExternalClasspathClassLoader() {
    super(parseUrls());
  }

  private static URL[] parseUrls() {
    final List<URL> urls = new ArrayList<URL>();
    final File file = new File(System.getProperty("classpath.file"));
    try {
      final BufferedReader reader = new BufferedReader(new FileReader(file));
      try {
        while(reader.ready()) {
          final String fileName = reader.readLine();
          urls.add(new File(fileName).toURI().toURL());
        }
      }
      finally {
        reader.close();
      }

      return urls.toArray(new URL[urls.size()]);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally {
      FileUtil.delete(file);
    }
  }
}
