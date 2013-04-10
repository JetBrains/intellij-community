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
package com.intellij.appengine.rt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class EnhancerRunner {
  public static void main(String[] args)
    throws IOException, NoSuchMethodException, ClassNotFoundException, InvocationTargetException, IllegalAccessException {
    File argsFile = new File(args[0]);
    String className = args[1];
    List<String> argsList = new ArrayList<String>();
    argsList.addAll(Arrays.asList(args).subList(2, args.length));
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
    final String[] allArgs = argsList.toArray(new String[argsList.size()]);
    delegate.getMethod("main", String[].class).invoke(null, (Object)allArgs);
  }
}
