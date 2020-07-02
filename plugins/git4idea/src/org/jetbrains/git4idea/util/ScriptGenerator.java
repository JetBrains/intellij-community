// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.git4idea.util;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import git4idea.config.GitExecutable;
import org.apache.commons.codec.DecoderException;
import org.apache.xmlrpc.XmlRpcClientLite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.git4idea.editor.GitRebaseEditorXmlRpcHandler;
import org.jetbrains.git4idea.http.GitAskPassXmlRpcHandler;
import org.jetbrains.git4idea.nativessh.GitNativeSshAskPassXmlRpcHandler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
  private final ArrayList<File> myPaths = new ArrayList<>();
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
    addClasses(XmlRpcClientLite.class, DecoderException.class);
  }

  /**
   * Add jar or directory that contains the class to the classpath
   *
   * @param classes classes which sources will be added
   */
  private void addClasses(final Class... classes) {
    for (Class<?> c : classes) {
      File classPath = new File(PathUtil.getJarPathForClass(c));
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
    FileUtil.writeToFile(file, content);
    FileUtil.setExecutable(file);
    return file;
  }

  @NotNull
  public File generate(@NotNull GitExecutable executable, boolean useBatchFile) throws IOException {
    String commandLine = commandLine(executable);
    return useBatchFile ? generateBatch(myPrefix, commandLine)
                        : generateShell(myPrefix, commandLine);
  }

  /**
   * @return a command line for the the executable program
   */
  public String commandLine(@NotNull GitExecutable executable) {
    StringBuilder cmd = new StringBuilder();

    if (executable instanceof GitExecutable.Wsl) {
      List<String> envs = ContainerUtil.newArrayList(
        GitNativeSshAskPassXmlRpcHandler.IJ_SSH_ASK_PASS_HANDLER_ENV,
        GitNativeSshAskPassXmlRpcHandler.IJ_SSH_ASK_PASS_PORT_ENV,
        GitAskPassXmlRpcHandler.IJ_ASK_PASS_HANDLER_ENV,
        GitAskPassXmlRpcHandler.IJ_ASK_PASS_PORT_ENV,
        GitRebaseEditorXmlRpcHandler.IJ_EDITOR_HANDLER_ENV);
      cmd.append("export WSLENV=");
      cmd.append(StringUtil.join(envs, it -> it + "/w", ":"));
      cmd.append("\n");

      cmd.append('"');
      File javaExecutable = new File(String.format("%s\\bin\\java.exe", System.getProperty("java.home")));
      cmd.append(executable.convertFilePath(javaExecutable));
      cmd.append('"');
    }
    else {
      cmd.append('"');
      cmd.append(String.format("%s/bin/java", System.getProperty("java.home")));
      cmd.append('"');
    }

    cmd.append(" -cp ");
    cmd.append('"');
    String classpathSeparator = String.valueOf(File.pathSeparatorChar);
    cmd.append(StringUtil.join(myPaths, file -> file.getPath(), classpathSeparator));
    cmd.append('"');

    cmd.append(' ');
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
