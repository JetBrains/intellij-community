// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.appengine.rt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class EnhancerRunner {
  public static void main(String[] args)
    throws IOException, NoSuchMethodException, ClassNotFoundException, InvocationTargetException, IllegalAccessException {
    File argsFile = new File(args[0]);
    String className = args[1];
    List<String> argsList = new ArrayList<String>(Arrays.asList(args).subList(2, args.length));
    BufferedReader reader = new BufferedReader(new FileReader(argsFile));
    try {
      while (reader.ready()) {
        final String arg = reader.readLine();
        argsList.add(arg);
      }
    }
    finally {
      reader.close();
    }
    argsFile.delete();

    final Class<?> delegate = Class.forName(className);
    final String[] allArgs = argsList.toArray(new String[0]);
    delegate.getMethod("main", String[].class).invoke(null, (Object)allArgs);
  }
}
