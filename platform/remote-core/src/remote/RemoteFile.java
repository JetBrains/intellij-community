// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import org.jetbrains.annotations.NotNull;

public final class RemoteFile {
  private final String myPath;
  private final boolean myWin;

  private RemoteFile(String path, boolean isWindows) {
    myPath = FileUtil.toSystemIndependentName(path).replace('/', getSeparator(isWindows));
    myWin = isWindows;
  }

  private RemoteFile(String parent, String child, boolean isWindows) {
    this(resolveChild(parent, child, isWindows), isWindows);
  }

  private static char getSeparator(boolean isWindows) {
    return isWindows ? '\\' : '/';
  }

  private static String resolveChild(String parent, String child, boolean win) {
    var separator = getSeparator(win);
    return parent.endsWith(String.valueOf(separator)) ? parent + child : parent + separator + child;
  }

  public @NotNull String getName() {
    int ind = myPath.lastIndexOf(getSeparator(myWin));
    if (ind != -1 && ind < myPath.length() - 1) { //not last char
      return myPath.substring(ind + 1);
    }
    else {
      return myPath;
    }
  }

  public String getPath() {
    return myPath;
  }

  public boolean isWin() {
    return isWindowsPath(myPath);
  }

  public static boolean isWindowsPath(@NotNull String path) {
    path = RemoteSdkProperties.getInterpreterPathFromFullPath(path);
    return OSAgnosticPathUtil.startsWithWindowsDrive(path);
  }

  public static @NotNull RemoteFile createRemoteFile(@NotNull String path) {
    return new RemoteFile(path, isWindowsPath(path));
  }

  public static @NotNull RemoteFile createRemoteFile(@NotNull String path, boolean isWindows) {
    return new RemoteFile(path, isWindows);
  }

  public static @NotNull RemoteFile createRemoteFile(@NotNull String parent, @NotNull String child) {
    return new RemoteFile(parent, child, isWindowsPath(parent));
  }

  public static @NotNull RemoteFile createRemoteFile(@NotNull String parent, @NotNull String child, boolean isWindows) {
    return new RemoteFile(parent, child, isWindows);
  }
}
