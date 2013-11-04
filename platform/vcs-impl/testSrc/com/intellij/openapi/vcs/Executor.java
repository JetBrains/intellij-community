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
package com.intellij.openapi.vcs;

import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 *
 * @author Kirill Likhodedov
 */
public class Executor {

  private static String ourCurrentDir;

  private static void cdAbs(String absolutePath) {
    ourCurrentDir = absolutePath;
    log("cd " + shortenPath(absolutePath));
  }

  private static void cdRel(String relativePath) {
    cdAbs(ourCurrentDir + "/" + relativePath);
  }

  public static void cd(String relativeOrAbsolutePath) {
    if (relativeOrAbsolutePath.startsWith("/") || relativeOrAbsolutePath.charAt(1) == ':') {
      cdAbs(relativeOrAbsolutePath);
    }
    else {
      cdRel(relativeOrAbsolutePath);
    }
  }

  public static void cd(VirtualFile dir) {
    cd(dir.getPath());
  }

  public static String pwd() {
    return ourCurrentDir;
  }

  public static String touch(String filePath) {
    try {
      File file = child(filePath);
      assert !file.exists() : "File " + file + " shouldn't exist yet";
      //noinspection ResultOfMethodCallIgnored
      new File(file.getParent()).mkdirs(); // ensure to create the directories
      boolean fileCreated = file.createNewFile();
      assert fileCreated;
      log("touch " + filePath);
      return file.getPath();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String touch(String fileName, String content) {
    String filePath = touch(fileName);
    echo(fileName, content);
    return filePath;
  }

  public static void echo(String fileName, String content) {
    try {
      FileUtil.writeToFile(child(fileName), content.getBytes(), true);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String mkdir(String dirName) {
    File file = child(dirName);
    boolean dirMade = file.mkdir();
    assert dirMade;
    log("mkdir " + dirName);
    return file.getPath();
  }

  public static String cat(String fileName) {
    try {
      String content = FileUtil.loadFile(child(fileName));
      log("cat " + fileName);
      return content;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void cp(String fileName, File destinationDir) {
    try {
      FileUtil.copy(child(fileName), new File(destinationDir, fileName));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static String run(List<String> params) {
    final ProcessBuilder builder = new ProcessBuilder().command(params);
    builder.directory(ourCurrentDir());
    builder.redirectErrorStream(true);
    Process clientProcess;
    try {
      clientProcess = builder.start();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    CapturingProcessHandler handler = new CapturingProcessHandler(clientProcess, CharsetToolkit.getDefaultSystemCharset());
    ProcessOutput result = handler.runProcess(30*1000);
    if (result.isTimeout()) {
      throw new RuntimeException("Timeout waiting for the command execution. Command: " + StringUtil.join(params, " "));
    }

    if (result.getExitCode() != 0) {
      log("{" + result.getExitCode() + "}");
    }
    String stdout = result.getStdout().trim();
    if (!StringUtil.isEmptyOrSpaces(stdout)) {
      log(stdout.trim());
    }
    return stdout;
  }

  protected static List<String> splitCommandInParameters(String command) {
    List<String> split = new ArrayList<String>();

    boolean insideParam = false;
    StringBuilder currentParam = new StringBuilder();
    for (char c : command.toCharArray()) {
      boolean flush = false;
      if (insideParam) {
        if (c == '\'') {
          insideParam = false;
          flush = true;
        }
        else {
          currentParam.append(c);
        }
      }
      else if (c == '\'') {
        insideParam = true;
      }
      else if (c == ' ') {
        flush = true;
      }
      else {
        currentParam.append(c);
      }

      if (flush) {
        if (!StringUtil.isEmptyOrSpaces(currentParam.toString())) {
          split.add(currentParam.toString());
        }
        currentParam = new StringBuilder();
      }
    }

    // last flush
    if (!StringUtil.isEmptyOrSpaces(currentParam.toString())) {
      split.add(currentParam.toString());
    }
    return split;
  }

  protected static String findExecutable(String programName, String unixExec, String winExec, Collection<String> pathEnvs) {
    String exec = findInPathEnvs(programName, pathEnvs);
    if (exec != null) {
      return exec;
    }
    exec = findInPath(programName, unixExec, winExec);
    if (exec != null) {
      return exec;
    }
    throw new IllegalStateException(programName + " executable not found. " +
                                    "Please define a valid environment variable " + pathEnvs.iterator().next() +
                                    " pointing to the " + programName + " executable.");
  }

  protected static String findInPath(String programName, String unixExec, String winExec) {
    String path = System.getenv(SystemInfo.isWindows ? "Path" : "PATH");
    if (path != null) {
      String name = SystemInfo.isWindows ? winExec : unixExec;
      for (String dir : path.split(File.pathSeparator)) {
        File file = new File(dir, name);
        if (file.canExecute()) {
          log("Using " + programName + " from PATH: " + file.getPath());
          return file.getPath();
        }
      }
    }
    return null;
  }

  protected static String findInPathEnvs(String programName, Collection<String> pathEnvs) {
    for (String pathEnv : pathEnvs) {
      String exec = System.getenv(pathEnv);
      if (exec != null && new File(exec).canExecute()) {
        log(String.format("Using %s from %s: %s", programName, pathEnv, exec));
        return exec;
      }
    }
    return null;
  }

  protected static void log(String msg) {
    System.out.println(msg);
  }

  private static String shortenPath(String path) {
    String[] split = path.split("/");
    if (split.length > 3) {
      // split[0] is empty, because the path starts from /
      return String.format("/%s/.../%s/%s", split[1], split[split.length-2], split[split.length-1]);
    }
    return path;
  }

  private static File child(String fileName) {
    assert ourCurrentDir != null : "Current dir hasn't been initialized yet. Call cd at least once before any other command.";
    return new File(ourCurrentDir, fileName);
  }

  private static File ourCurrentDir() {
    assert ourCurrentDir != null : "Current dir hasn't been initialized yet. Call cd at least once before any other command.";
    return new File(ourCurrentDir);
  }

}
