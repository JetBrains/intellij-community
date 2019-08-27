// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.Pair;
import com.intellij.util.AbstractPathMapper;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.PathMapper;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class RemoteProcessUtil {
  /**
   * Builds {@code &lt;command, working directory&gt;} tuple appropriate for execution on the remote host for provided {@code command} and
   * {@code workingDir} using specified {@code pathMapper}.
   */
  public static Pair<String[], String> buildRemoteCommandLine(@NotNull AbstractPathMapper pathMapper,
                                                              @NotNull String[] command,
                                                              @Nullable String workingDir,
                                                              @NotNull String interpreterPath) {
    command = Arrays.copyOf(command, command.length);

    for (int i = 0; i < command.length; i++) {
      if (pathMapper.canReplaceLocal(command[i])) {
        command[i] = RemoteFile.detectSystemByPath(interpreterPath).
          createRemoteFile(pathMapper.convertToRemote(command[i])).getPath();
      }
      if (isPathList(command[i])) {
        command[i] = remapPathsList(command[i], pathMapper, interpreterPath);
      }
    }
    command[1] = pathMapper.convertToRemote(command[1]); //convert helper path
    command[0] = interpreterPath;

    return Pair.create(command, toRemoteFileSystemStyle(workingDir));
  }

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
    List<String> paths = Lists.newArrayList(pathsValue.split(File.pathSeparator));
    List<String> mappedPaths = Lists.newArrayList();

    for (String path : paths) {
      mappedPaths.add(new RemoteFile(pathMapper.convertToRemote(path), isWin).getPath());
    }
    return Joiner.on(isWin ? ';' : ':').join(mappedPaths);
  }

  private static boolean isPathList(String value) {
    List<String> paths = Lists.newArrayList(value.split(File.pathSeparator));

    if (paths.size() > 1) {
      for (String p : paths) {
        if (new File(p).exists()) {
          return true;
        }
      }
    }

    return false;
  }
}
