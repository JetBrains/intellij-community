// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** @deprecated please use {@link com.sun.jna.platform.win32.Advapi32Util JNA} instead. */
@Deprecated
@ApiStatus.Internal
public final class WindowsRegistryUtil {
  private WindowsRegistryUtil() { }

  private static @Nullable String trimToValue(@Nullable StringBuilder output) {
    if (output == null) {
      return null;
    }
    int pos = output.lastIndexOf("  ");
    int pos2 = output.lastIndexOf("\t");
    pos = Math.max(pos, pos2);
    if (pos == -1) {
      return null;
    }

    output.delete(0, pos + 1);
    String blackList = "\r\n \"";
    int startPos = 0;
    int endPos = output.length() - 1;
    while (true) {
      if (startPos >= endPos) {
        return null;
      }
      if (blackList.indexOf(output.charAt(startPos)) != -1) {
        startPos++;
      }
      else {
        break;
      }
    }
    while (blackList.indexOf(output.charAt(endPos)) != -1) {
      endPos--;
    }
    return output.subSequence(startPos, endPos + 1).toString();
  }

  public static @NotNull List<String> readRegistryBranch(@NotNull String location) {
    List<String> result = new ArrayList<>();
    StringBuilder output = doReadBranch(location);
    if (output != null) {
      for (int pos = output.indexOf(location); pos != -1; pos = output.indexOf(location, pos + location.length())) {
        int pos2 = output.indexOf("\r\n", pos + location.length());
        if (pos2 <= pos + location.length()) {
          continue;
        }
        String section = output.substring(pos + location.length() + 1, pos2);
        if (!section.contains("\\")) {
          result.add(section);
        }
      }
    }
    return result;
  }

  private static StringBuilder doReadBranch(@NotNull String location) {
    return readRegistrySilently("reg query \"" + location + "\"");
  }

  public static @Nullable String readRegistryDefault(@NonNls @NotNull String location) {
    return trimToValue(readRegistrySilently("reg query \"" + location + "\" /ve"));
  }

  public static @Nullable String readRegistryValue(@NonNls @NotNull String location, @NonNls @NotNull String key) {
    return trimToValue(readRegistrySilently("reg query \"" + location + "\" /v " + key));
  }

  private static @Nullable StringBuilder readRegistrySilently(@NonNls @NotNull String command) {
    try {
      String text = readRegistry(command);
      return new StringBuilder(text);
    }
    catch (Exception e) {
      return null;
    }
  }

  @ApiStatus.Internal
  public static @NotNull String readRegistry(@NonNls @NotNull String command) throws IOException, InterruptedException {
    Process process = Runtime.getRuntime().exec(command);
    InputStream is = null;
    ByteArrayOutputStream os = null;
    try {
      byte[] buffer = new byte[128];
      is = process.getInputStream();
      os = new ByteArrayOutputStream();
      for (int length = is.read(buffer); length > 0; length = is.read(buffer)) {
        os.write(buffer, 0, length);
      }
      return new String(os.toByteArray(), StandardCharsets.UTF_8);
    }
    finally {
      if (is != null) is.close();
      if (os != null) os.close();
      process.waitFor();
    }
  }
}