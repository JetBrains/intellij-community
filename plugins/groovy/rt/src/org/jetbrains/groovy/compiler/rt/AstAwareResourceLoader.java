// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
