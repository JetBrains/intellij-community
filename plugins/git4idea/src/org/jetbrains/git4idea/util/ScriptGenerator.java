/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Script generator utility class. It uses to generate a temporary scripts that
 * are removed after application ends.
 */
public class ScriptGenerator {
  /**
   * The extension of the ssh script name
   */
  public static final String SCRIPT_EXT = SystemInfo.isWindows ? ".bat" : ".sh";

  /**
   * The script prefix
   */
  private final String myPrefix;
  /**
   * The scripts may class
   */
  private final Class myMainClass;
  /**
   * The class paths for the script
   */
  private final ArrayList<String> myPaths = new ArrayList<>();
  /**
   * The internal parameters for the script
   */
  private final ArrayList<String> myInternalParameters = new ArrayList<>();

  /**
   * A constructor
   *
   * @param prefix    the script prefix
   * @param mainClass the script main class
   */
  public ScriptGenerator(final String prefix, final Class mainClass) {
    myPrefix = prefix;
    myMainClass = mainClass;
    addClasses(myMainClass);
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
  @NotNull
  public File generate() throws IOException {
    String title = SystemInfo.isWindows ? "@echo off" : "#!/bin/sh";
    String parametersPassthrough = SystemInfo.isWindows ? " %*" : " \"$@\"";
    String content = title + "\n" + commandLine() + parametersPassthrough + "\n";
    File file = new File(PathManager.getTempPath(), myPrefix + SCRIPT_EXT);
    if (SystemInfo.isWindows && file.getPath().contains(" ")) {
      file = new File(FileUtil.getTempDirectory(), myPrefix + SCRIPT_EXT);
    }
    FileUtil.writeToFile(file, content);
    FileUtil.setExecutableAttribute(file.getPath(), true);
    return file;
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
