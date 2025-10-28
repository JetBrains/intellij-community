// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A helper class for reading a list of `--add-opens` JVM options from a specified file
 * and filtering out ones inappropriate for the given platform.
 */
public final class JavaModuleOptions {
  private JavaModuleOptions() { }

  public static @NotNull List<String> readOptions(@NotNull Path source, @NotNull OS os) throws IOException {
    List<String> exclusions = getExclusions(os);
    try (Stream<String> lines = Files.lines(source)) {
      return lines.filter(line -> !ContainerUtil.exists(exclusions, line::contains)).collect(Collectors.toList());
    }
  }

  public static @NotNull List<String> readOptions(@NotNull InputStream source, @NotNull OS os) throws IOException {
    List<String> result = new ArrayList<>();
    List<String> exclusions = getExclusions(os);
    BufferedReader reader = new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8));
    String line;
    while ((line = reader.readLine()) != null) {
      if (!ContainerUtil.exists(exclusions, line::contains)) {
        result.add(line);
      }
    }
    return result;
  }

  @SuppressWarnings("SpellCheckingInspection")
  private static List<String> getExclusions(OS os) {
    List<String> exclusions = new ArrayList<>(2);
    if (os != OS.Windows) {
      exclusions.add("/sun.awt.windows");
    }
    if (os != OS.macOS) {
      exclusions.add("/sun.lwawt");
      exclusions.add("/com.apple");
    }
    if (os != OS.Linux && os != OS.FreeBSD) {
      exclusions.add("/sun.awt.X11");
      exclusions.add("/com.sun.java.swing.plaf.gtk");
    }
    return exclusions;
  }
}
