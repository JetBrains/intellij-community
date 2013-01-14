/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.groovy.compiler.rt;

import groovy.lang.GroovyResourceLoader;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

public class AstAwareResourceLoader implements GroovyResourceLoader {
  final Map<String, File> myClass2File;

  AstAwareResourceLoader(Map<String, File> class2File) {
    myClass2File = Collections.synchronizedMap(class2File);
  }

  public URL loadGroovySource(String className) throws MalformedURLException {
    if (className == null) return null;

    File file = getSourceFile(className);
    if (file != null && file.exists()) {
      return file.toURL();
    }

    file = new File(className);
    if (file.exists()) {
      return file.toURL();
    }

    return null;
  }

  File getSourceFile(String className) {
    return myClass2File.get(className);
  }
}
