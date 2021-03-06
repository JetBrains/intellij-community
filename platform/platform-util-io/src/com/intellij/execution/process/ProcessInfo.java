// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class ProcessInfo {
  public static final ProcessInfo[] EMPTY_ARRAY = new ProcessInfo[0];

  private final int myPid;
  @NotNull private final String myCommandLine;
  @NotNull private final Optional<String> myExecutablePath;
  @NotNull private final String myExecutableName;
  @NotNull private final String myArgs;

  public ProcessInfo(int pid,
                     @NotNull String commandLine,
                     @NotNull String executableName,
                     @NotNull String args) {
    myPid = pid;
    myCommandLine = commandLine;
    myExecutablePath = Optional.empty();
    myExecutableName = executableName;
    myArgs = args;
  }

  public ProcessInfo(int pid,
                     @NotNull String commandLine,
                     @NotNull String executableName,
                     @NotNull String args,
                     @Nullable String executablePath) {
    myPid = pid;
    myCommandLine = commandLine;
    myExecutableName = executableName;
    myExecutablePath = StringUtil.isNotEmpty(executablePath) ? Optional.of(executablePath) : Optional.empty();
    myArgs = args;
  }

  public int getPid() {
    return myPid;
  }

  @NotNull
  @NlsSafe
  public String getCommandLine() {
    return myCommandLine;
  }

  @NotNull
  @NlsSafe
  public String getExecutableName() {
    return myExecutableName;
  }

  @NotNull
  @NlsSafe
  public Optional<String> getExecutableCannonicalPath() {
    return myExecutablePath.map(s -> {
      try {
        return new File(s).getCanonicalPath();
      }
      catch (IOException e) {
        return s;
      }
    });
  }

  @NotNull
  @NlsSafe
  public String getExecutableDisplayName() {
    return StringUtil.trimEnd(myExecutableName, ".exe", true);
  }

  @NotNull
  @NlsSafe
  public String getArgs() {
    return myArgs;
  }

  @Override
  public String toString() {
    return myPid + " '" + myCommandLine + "' '" + myExecutableName + "' '" + myArgs + "'" +
           myExecutablePath.map(s -> " " + s).orElse("");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ProcessInfo info = (ProcessInfo)o;

    if (myPid != info.myPid) return false;
    if (!myExecutableName.equals(info.myExecutableName)) return false;
    if (!myArgs.equals(info.myArgs)) return false;
    if (!myCommandLine.equals(info.myCommandLine)) return false;
    if (!myExecutablePath.equals(info.myExecutablePath)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myPid;
    result = 31 * result + myExecutableName.hashCode();
    result = 31 * result + myArgs.hashCode();
    result = 31 * result + myCommandLine.hashCode();
    return result;
  }
}