// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.groovy.compiler.rt;

import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public final class GroovycRunner {

  public static void main(String[] args) {
    System.exit(intMain(args));
  }

  public static int intMain(String[] args) {
    boolean indy = false;
    if (args.length != 3) {
      if (args.length != 4 || !"--indy".equals(args[3])) {
        //noinspection UseOfSystemOutOrSystemErr
        System.err.println("There is no arguments for groovy compiler");
        return 1;
      }
      indy = true;
    }

    final boolean optimize = GroovyRtConstants.OPTIMIZE.equals(args[0]);
    final boolean forStubs = "stubs".equals(args[1]);
    String argPath = args[2];

    String configScript = System.getProperty(GroovyRtConstants.GROOVYC_CONFIG_SCRIPT);

    //noinspection UseOfSystemOutOrSystemErr
    return intMain2(indy, optimize, forStubs, argPath, configScript, null, null, System.out, System.err);
  }

  public static int intMain2(boolean indy, boolean optimize, boolean forStubs,
                             String argPath, String configScript,
                             @Nullable String targetBytecode,
                             @Nullable Queue<? super Object> mailbox,
                             @NotNull PrintStream out,
                             @NotNull PrintStream err) {
    if (indy) {
      System.setProperty("groovy.target.indy", "true");
    }

    if (!new File(argPath).exists()) {
      err.println("Arguments file for groovy compiler not found");
      return 1;
    }

    ClassLoader loader = optimize ? buildMainLoader(argPath, err) : GroovycRunner.class.getClassLoader();
    if (loader == null) {
      err.println("Cannot find class loader for groovyc; optimized=" + optimize + "; " + GroovycRunner.class.getClassLoader());
      return 1;
    }
    if (optimize) {
      Thread.currentThread().setContextClassLoader(loader);
    }

    try {
      Class.forName("org.codehaus.groovy.control.CompilationUnit", true, loader);
    }
    catch (Throwable e) {
      err.println(GroovyRtConstants.NO_GROOVY);
      e.printStackTrace(err);
      return 1;
    }

    try {
      Class<?> aClass = Class.forName("org.jetbrains.groovy.compiler.rt.DependentGroovycRunner", true, loader);
      Method method = aClass.getDeclaredMethod("runGroovyc", boolean.class, String.class, String.class, String.class,
                                               Queue.class, PrintStream.class, PrintStream.class);
      method.invoke(null, Boolean.valueOf(forStubs), argPath, configScript, targetBytecode, mailbox, out, err);
    }
    catch (Throwable e) {
      //noinspection InstanceofCatchParameter
      while (e instanceof InvocationTargetException) {
        e = e.getCause();
      }
      e.printStackTrace(err);
      return 1;
    }
    return 0;
  }

  @Nullable
  private static ClassLoader buildMainLoader(String argsPath, PrintStream err) {
    final List<URL> urls = new ArrayList<URL>();
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(argsPath), Charset.forName("UTF-8")));
      String classpath = reader.readLine();
      for (String s : classpath.split(File.pathSeparator)) {
        urls.add(new File(s).toURI().toURL());
      }
      reader.close();
    }
    catch (IOException e) {
      e.printStackTrace(err);
      return null;
    }

    final ClassLoader[] ref = new ClassLoader[1];
    new Runnable() {
      public void run() {
        ref[0] = UrlClassLoader.build().urls(urls).useCache().allowLock().get();
      }
    }.run();
    return ref[0];
  }
}
