/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.groovy.compiler.rt;

import com.intellij.util.lang.UrlClassLoader;
import sun.misc.URLClassPath;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 16.04.2007
 * @noinspection UseOfSystemOutOrSystemErr,CallToPrintStackTrace
 */

public class GroovycRunner {

  private GroovycRunner() {
  }

  /*
  private static Controller initController() {
    if (!"true".equals(System.getProperty("profile.groovy.compiler"))) {
      return null;
    }

    try {
      return new Controller();
    }
    catch (Exception ex) {
      ex.printStackTrace();
      return null;
    }
  }
  */


  public static void main(String[] args) {
    /*
    if (ourController != null) {
      try {
        ourController.startCPUProfiling(ProfilingModes.CPU_SAMPLING, null);
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
    */

    if (args.length != 3) {
      if (args.length != 4 || !"--indy".equals(args[3])) {
        System.err.println("There is no arguments for groovy compiler");
        System.exit(1);
      }
      System.setProperty("groovy.target.indy", "true");
    }

    final boolean optimize = GroovyRtConstants.OPTIMIZE.equals(args[0]);
    final boolean forStubs = "stubs".equals(args[1]);
    String argPath = args[2];

    if (!new File(argPath).exists()) {
      System.err.println("Arguments file for groovy compiler not found");
      System.exit(1);
    }

    ClassLoader loader = optimize ? buildMainLoader(argPath) : GroovycRunner.class.getClassLoader();
    Thread.currentThread().setContextClassLoader(loader);

    try {
      Class.forName("org.codehaus.groovy.control.CompilationUnit", true, loader);
    }
    catch (Throwable e) {
      System.err.println(GroovyRtConstants.NO_GROOVY);
      System.exit(1);
    }

    try {
      Class<?> aClass = Class.forName("org.jetbrains.groovy.compiler.rt.DependentGroovycRunner", true, loader);
      Method method = aClass.getDeclaredMethod("runGroovyc", boolean.class, String.class);
      method.invoke(null, forStubs, argPath);
    }
    catch (Throwable e) {
      e.printStackTrace();
      System.exit(1);
    }
    /*
    finally {
      if (ourController != null) {
        try {
          ourController.captureSnapshot(ProfilingModes.SNAPSHOT_WITHOUT_HEAP);
          ourController.stopCPUProfiling();
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
    */
  }

  private static ClassLoader buildMainLoader(String argsPath) {
    Set<URL> bootstrapUrls = new HashSet<URL>();
    try {
      Method method = ClassLoader.class.getDeclaredMethod("getBootstrapClassPath");
      method.setAccessible(true);
      URLClassPath ucp = (URLClassPath)method.invoke(null);
      Collections.addAll(bootstrapUrls, ucp.getURLs());
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    final List<URL> urls = new ArrayList<URL>();
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      BufferedReader reader = new BufferedReader(new FileReader(argsPath));
      String classpath = reader.readLine();
      for (String s : classpath.split(File.pathSeparator)) {
        URL url = new File(s).toURI().toURL();
        if (!bootstrapUrls.contains(url)) {
          urls.add(url);
        }
      }
      reader.close();
    }
    catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
    }

    final ClassLoader[] ref = new ClassLoader[1];
    new Runnable() {
      public void run() {
        ref[0] = UrlClassLoader.build().urls(urls).useCache().get();
      }
    }.run();
    return ref[0];
  }
}
