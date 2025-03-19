// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public class ProcessInfo {
  public static final ProcessInfo[] EMPTY_ARRAY = new ProcessInfo[0];

  private final int myPid;
  private final int myParentPid;
  private final @NotNull String myCommandLine;
  private final @NotNull Optional<String> myExecutablePath;
  private final @NotNull String myExecutableName;
  private final @NotNull String myArgs;
  private final @Nullable String myUser;
  private final @NotNull ThreeState myOwnedByCurrentUser;

  public ProcessInfo(int pid,
                     @NotNull String commandLine,
                     @NotNull String executableName,
                     @NotNull String args) {
    this(pid, commandLine, executableName, args, null, -1);
  }

  public ProcessInfo(int pid,
                     @NotNull String commandLine,
                     @NotNull String executableName,
                     @NotNull String args,
                     @Nullable String executablePath) {
    this(pid, commandLine, executableName, args, executablePath, -1);
  }

  public ProcessInfo(int pid,
                     @NotNull String commandLine,
                     @NotNull String executableName,
                     @NotNull String args,
                     @Nullable String executablePath,
                     int parentPid) {
    this(pid, commandLine, executableName, args, executablePath, parentPid, null, ThreeState.UNSURE);
  }

  public ProcessInfo(int pid,
                     @NotNull String commandLine,
                     @NotNull String executableName,
                     @NotNull String args,
                     @Nullable String executablePath,
                     int parentPid,
                     @Nullable String user,
                     @NotNull ThreeState isOwnedByCurrentUser) {
    myPid = pid;
    myCommandLine = commandLine;
    myExecutableName = executableName;
    myExecutablePath = StringUtil.isNotEmpty(executablePath) ? Optional.of(executablePath) : Optional.empty();
    myArgs = args;
    myParentPid = parentPid;
    myUser = user;
    myOwnedByCurrentUser = isOwnedByCurrentUser;
  }

  public int getPid() {
    return myPid;
  }

  public int getParentPid() {
    return myParentPid;
  }

  public @NotNull @NlsSafe String getCommandLine() {
    return myCommandLine;
  }

  public @NotNull @NlsSafe String getExecutableName() {
    return myExecutableName;
  }

  public @NotNull @NlsSafe Optional<String> getExecutableCannonicalPath() {
    return myExecutablePath.map(s -> {
      try {
        return new File(s).getCanonicalPath();
      }
      catch (IOException e) {
        return s;
      }
    });
  }

  public @NotNull @NlsSafe String getExecutableDisplayName() {
    return StringUtil.trimEnd(myExecutableName, ".exe", true);
  }

  public @NotNull @NlsSafe String getArgs() {
    return myArgs;
  }

  public @Nullable @NlsSafe String getUser() {
    return myUser;
  }

  /**
   * @return {@link ThreeState#UNSURE} if there is no information about the current user,
   * {@link ThreeState#YES} if process' user matches the current user who listed processes,
   * {@link ThreeState#NO} otherwise.
   */
  public @NotNull ThreeState isOwnedByCurrentUser() {
    return myOwnedByCurrentUser;
  }

  @Override
  public String toString() {
    return myPid + (myUser != null ? " " + myUser : "") + " '" + myCommandLine + "' '" + myExecutableName + "' '" + myArgs + "'" +
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
    if (myParentPid != info.myParentPid) return false;
    if (!Objects.equals(myUser, ((ProcessInfo)o).myUser)) return false;
    if (!myOwnedByCurrentUser.equals(((ProcessInfo)o).myOwnedByCurrentUser)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = myPid;
    result = 31 * result + myExecutableName.hashCode();
    result = 31 * result + myArgs.hashCode();
    result = 31 * result + myCommandLine.hashCode();
    result = 31 * result + myParentPid;
    result = 31 * result + (myUser != null ? myUser.hashCode() : 0);
    result = 31 * result + myOwnedByCurrentUser.hashCode();
    return result;
  }
}