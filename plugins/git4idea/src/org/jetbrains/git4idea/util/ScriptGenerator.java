/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.git4idea.util;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Script generator utility class. It uses to generate a temporary scripts that
 * are removed after application ends.
 */
public class ScriptGenerator {
  /**
   * The extension of the ssh script name
   */
  @NonNls public static final String SCRIPT_EXT;

  static {
    if (SystemInfo.isWindows) {
      SCRIPT_EXT = ".bat";
    }
    else {
      SCRIPT_EXT = ".sh";
    }
  }

  /**
   * The script prefix
   */
  private final String myPrefix;
  /**
   * The scripts may class
   */
  private final Class myMainClass;
  /**
   * The temporary directory to use or null, if system directory should be used
   */
  @Nullable private final File myTempDir;
  /**
   * The class paths for the script
   */
  private final ArrayList<String> myPaths = new ArrayList<String>();
  /**
   * The internal parameters for the script
   */
  private final ArrayList<String> myInternalParameters = new ArrayList<String>();

  /**
   * A constructor
   *
   * @param prefix    the script prefix
   * @param mainClass the script main class
   * @param tempDir   the temporary directory to use. if null, system default is used.
   */
  public ScriptGenerator(final String prefix, final Class mainClass, File tempDir) {
    myPrefix = prefix;
    myMainClass = mainClass;
    myTempDir = tempDir;
    addClasses(myMainClass);
  }

  /**
   * A constructor
   *
   * @param prefix    the script prefix
   * @param mainClass the script main class
   */
  public ScriptGenerator(final String prefix, final Class mainClass) {
    this(prefix, mainClass, null);
  }

  /**
   * Add jar or directory that contains the class to the classpath
   *
   * @param classes classes which sources will be added
   * @return this script generator
   */
  public ScriptGenerator addClasses(final Class... classes) {
    for (Class<?> c : classes) {
      addPath(PathUtil.getJarPathForClass(c));
    }
    return this;
  }

  /**
   * Add path to class path. The methods checks if the path has been already added to the classpath.
   *
   * @param path the path to add
   */
  private void addPath(final String path) {
    if (!myPaths.contains(path)) {
      // the size of path is expected to be quite small, so no optimization is done here
      myPaths.add(path);
    }
  }

  /**
   * Add source for the specified resource
   *
   * @param base     the resource base
   * @param resource the resource name
   * @return this script generator
   */
  public ScriptGenerator addResource(final Class base, @NonNls String resource) {
    addPath(getJarForResource(base, resource));
    return this;
  }

  /**
   * Add internal parameters for the script
   *
   * @param parameters internal parameters
   * @return this script generator
   */
  public ScriptGenerator addInternal(String... parameters) {
    ContainerUtil.addAll(myInternalParameters, parameters);
    return this;
  }

  /**
   * Generate script according to specified parameters
   *
   * @return the path to generated script
   * @throws IOException if there is a problem with creating script
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  public File generate() throws IOException {
    File scriptPath = myTempDir != null ? File.createTempFile(myPrefix, SCRIPT_EXT, myTempDir)
                                        : File.createTempFile(myPrefix, SCRIPT_EXT);
    scriptPath.deleteOnExit();
    PrintWriter out = new PrintWriter(new FileWriter(scriptPath));
    try {
      if (SystemInfo.isWindows) {
        out.println("@echo off");
      }
      else {
        out.println("#!/bin/sh");
      }
      String line = commandLine();
      if (SystemInfo.isWindows) {
        line += " %*";
      }
      else {
        line += " \"$@\"";
      }
      out.println(line);
    }
    finally {
      out.close();
    }
    FileUtil.setExectuableAttribute(scriptPath.getPath(), true);
    return scriptPath;
  }

  /**
   * @return a command line for the the executable program
   */
  public String commandLine() {
    StringBuilder cmd = new StringBuilder();
    cmd.append('\"').append(System.getProperty("java.home")).append(File.separatorChar).append("bin").append(File.separatorChar)
      .append("java\" -cp \"");
    boolean first = true;
    for (String p : myPaths) {
      if (!first) {
        cmd.append(File.pathSeparatorChar);
      }
      else {
        first = false;
      }
      cmd.append(p);
    }
    cmd.append("\" ");
    cmd.append(myMainClass.getName());
    for (String p : myInternalParameters) {
      cmd.append(' ');
      cmd.append(p);
    }
    String line = cmd.toString();
    if (SystemInfo.isWindows) {
      line = line.replace('\\', '/');
    }
    return line;
  }

  /**
   * Get path for resources.jar
   *
   * @param context a context class
   * @param res     a resource
   * @return a path to classpath entry
   */
  @SuppressWarnings({"SameParameterValue"})
  public static String getJarForResource(Class context, String res) {
    String resourceRoot = PathManager.getResourceRoot(context, res);
    return new File(resourceRoot).getAbsoluteFile().getAbsolutePath();
  }
}
