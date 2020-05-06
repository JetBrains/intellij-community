// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.git4idea.util;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.apache.commons.codec.DecoderException;
import org.apache.xmlrpc.XmlRpcClientLite;
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
  public ScriptGenerator(@NotNull String prefix, @NotNull Class mainClass) {
    myPrefix = prefix;
    myMainClass = mainClass;
    addClasses(myMainClass);
    addClasses(XmlRpcClientLite.class, DecoderException.class, StringUtilRt.class);
  }

  /**
   * Add jar or directory that contains the class to the classpath
   *
   * @param classes classes which sources will be added
   */
  private void addClasses(final Class... classes) {
    for (Class<?> c : classes) {
      String classPath = PathUtil.getJarPathForClass(c);
      if (!myPaths.contains(classPath)) {
        // the size of path is expected to be quite small, so no optimization is done here
        myPaths.add(classPath);
      }
    }
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

  @NotNull
  private static File generateBatch(@NotNull String fileName, @NotNull String commandLine) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("@echo off").append("\n");
    sb.append(commandLine).append(" %*").append("\n");
    return createTempExecutable(fileName + ".bat", sb.toString());
  }

  @NotNull
  private static File generateShell(@NotNull String fileName, @NotNull String commandLine) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("#!/bin/sh").append("\n");
    sb.append(commandLine).append(" \"$@\"").append("\n");
    return createTempExecutable(fileName + ".sh", sb.toString());
  }

  @NotNull
  private static File createTempExecutable(@NotNull String fileName, @NotNull String content) throws IOException {
    File file = new File(PathManager.getTempPath(), fileName);
    if (SystemInfo.isWindows && file.getPath().contains(" ")) {
      file = new File(FileUtil.getTempDirectory(), fileName);
    }
    FileUtil.writeToFile(file, content);
    FileUtil.setExecutable(file);
    return file;
  }

  @NotNull
  public File generate(boolean useBatchFile) throws IOException {
    String commandLine = commandLine();
    return useBatchFile ? generateBatch(myPrefix, commandLine)
                        : generateShell(myPrefix, commandLine);
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
}
