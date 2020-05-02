// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.google.common.base.Joiner;
import com.intellij.util.AbstractPathMapper;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.PathMapper;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class RemoteProcessUtil {
  @Contract("null -> null")
  public static String toRemoteFileSystemStyle(@Nullable String path) {
    if (path == null) {
      return null;
    }
    return RemoteFile.detectSystemByPath(path).createRemoteFile(path).getPath();
  }

  public static String[] buildRemoteCommand(@NotNull AbstractPathMapper pathMapper, @NotNull Collection<String> commands) {
    return ArrayUtilRt.toStringArray(pathMapper.convertToRemote(commands));
  }

  @NotNull
  public static String remapPathsList(@NotNull String pathsValue, @NotNull PathMapper pathMapper, @NotNull String interpreterPath) {
    boolean isWin = RemoteFile.isWindowsPath(interpreterPath);
    List<String> mappedPaths = new ArrayList<>();
    for (String path : pathsValue.split(File.pathSeparator)) {
      mappedPaths.add(new RemoteFile(pathMapper.convertToRemote(path), isWin).getPath());
    }
    return Joiner.on(isWin ? ';' : ':').join(mappedPaths);
  }
}
