/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.android.compiler.tools;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace", "SSBasedInspection"})
public class AndroidDxRunner {
  @NonNls private final static String DEX_MAIN = "com.android.dx.command.dexer.Main";
  @NonNls private final static String DEX_CONSOLE = "com.android.dx.command.DxConsole";
  @NonNls private final static String DEX_ARGS = "com.android.dx.command.dexer.Main$Arguments";

  @NonNls private final static String MAIN_RUN = "run";

  private static Method myMethod;

  private static Constructor<?> myConstructor;
  private static Field myOutNameField;
  private static Field myVerboseField;
  private static Field myJarOutputField;
  private static Field myFileNamesField;
  private static Field myStrictNameCheckField;

  private static Field myConsoleOut;
  private static Field myConsoleErr;

  private AndroidDxRunner() { }

  private static void loadDex(String dxPath) {
    try {
      File f = new File(dxPath);
      if (!f.isFile()) {
        System.err.println("File not found: " + dxPath);
        return;
      }
      URL url = f.toURI().toURL();

      URLClassLoader loader = new URLClassLoader(new URL[]{url}, AndroidDxRunner.class.getClassLoader());

      Class<?> mainClass = loader.loadClass(DEX_MAIN);
      Class<?> consoleClass = loader.loadClass(DEX_CONSOLE);
      Class<?> argClass = loader.loadClass(DEX_ARGS);

      myMethod = mainClass.getMethod(MAIN_RUN, argClass);

      myConstructor = argClass.getConstructor();
      myOutNameField = argClass.getField("outName");
      myJarOutputField = argClass.getField("jarOutput");
      myFileNamesField = argClass.getField("fileNames");
      myVerboseField = argClass.getField("verbose");
      myStrictNameCheckField = argClass.getField("strictNameCheck");

      myConsoleOut = consoleClass.getField("out");
      myConsoleErr = consoleClass.getField("err");
    }
    catch (SecurityException e) {
      reportError("Unable to find API for dex.jar", e);
    }
    catch (NoSuchMethodException e) {
      reportError("Unable to find method for dex.jar", e);
    }
    catch (NoSuchFieldException e) {
      reportError("Unable to find field for dex.jar", e);
    }
    catch (MalformedURLException e) {
      reportError("Failed to load dx.jar", e);
    }
    catch (ClassNotFoundException e) {
      reportError("Failed to load dx.jar", e);
    }
  }

  private static int runDex(String dxPath, String outFilePath, String[] fileNames) {
    loadDex(dxPath);

    try {
      myConsoleErr.set(null, System.err);
      myConsoleOut.set(null, System.out);

      Object args = myConstructor.newInstance();
      myOutNameField.set(args, outFilePath);
      myFileNamesField.set(args, fileNames);
      myJarOutputField.set(args, false);
      myVerboseField.set(args, false);
      myStrictNameCheckField.set(args, false);

      Object res = myMethod.invoke(null, args);

      if (res instanceof Integer) {
        return ((Integer)res).intValue();
      }
    }
    catch (IllegalAccessException e) {
      reportError("Unable to execute DX", e);
    }
    catch (InstantiationException e) {
      reportError("Unable to execute DX", e);
    }
    catch (InvocationTargetException e) {
      Throwable targetException = e.getTargetException();
      reportError("Unable to execute DX", targetException != null ? targetException : e);
    }
    return -1;
  }

  private static void reportError(String message, Throwable t) {
    System.err.println(message);
    t.printStackTrace();
  }

  private static void reportWarning(String message) {
    System.err.println("warning: " + message);
  }

  private static void collectFiles(File root, Collection<String> result, Set<String> visited, Set<String> qNames) throws IOException {
    collectFiles(root.getParentFile(), root, result, visited, qNames);
  }

  private static void collectFiles(File root, File file, Collection<String> result, Set<String> visited, Set<String> qNames)
    throws IOException {
    String path = file.getCanonicalPath();
    if (!visited.add(path)) {
      return;
    }
    
    if (file.isDirectory()) {
      final File[] children = file.listFiles();

      if (children != null) {
        for (File child : children) {
          collectFiles(root, child, result, visited, qNames);
        }
      }
    }
    else {
      if ("class".equals(FileUtilRt.getExtension(file.getName()))) {
        final String qName = getQualifiedName(root, file);
        if (qName != null && !qNames.add(qName)) {
          reportWarning(FileUtilRt.toSystemDependentName(file.getPath()) + " won't be added. Class " +
                        qName + " already exists in classpath");
          return;
        }
      }
      result.add(path);
    }
  }

  @Nullable
  private static String getQualifiedName(File root, File classFile) {
    String relativePath = FileUtilRt.getRelativePath(root, classFile);
    if (relativePath == null) {
      return null;
    }

    return FileUtilRt.getNameWithoutExtension(FileUtilRt.toSystemIndependentName(relativePath)).replace('/', '.');
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      System.err.println("Error: dx path must be passed as first argument");
    }
    String dxPath = args[0];
    if (args.length == 1) {
      System.err.println("Error: out file path must be passed as second argument");
    }
    String outFilePath = args[1];
    if (args.length == 2) {
      System.err.println("Error: no files");
    }

    Set<String> files = new HashSet<String>();
    HashSet<String> visited = new HashSet<String>();
    HashSet<String> qNames = new HashSet<String>();

    int i = 2;
    while (i < args.length) {
      String arg = args[i];
      if ("--exclude".equals(arg)) {
        break;
      }
      File file = new File(arg);
      if (file.exists()) {
        try {
          collectFiles(file, files, visited, qNames);
        }
        catch (IOException e) {
          reportError("I/O error", e);
        }
      }
      i++;
    }
    String[] excludedFiles = new String[args.length - i - 1];
    System.arraycopy(args, i + 1, excludedFiles, 0, excludedFiles.length);
    files.removeAll(Arrays.asList(excludedFiles));
    String[] filesArray = files.toArray(new String[files.size()]);
    //System.out.println("file names: " + concat(filesArray));
    runDex(dxPath, outFilePath, filesArray);
  }

  private static String concat(String[] ar) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < ar.length; i++) {
      builder.append('"').append(ar[i]).append('"');
      if (i < ar.length - 1) {
        builder.append(", ");
      }
    }
    return builder.toString();
  }
}
