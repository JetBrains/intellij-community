// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

public final class CommandTestHelper {
  public static final String ARG = "-arg";
  public static final String ENV = "-env";
  public static final String OUT = "-out";

  public static void main(String[] args) throws IOException {
    String mode = null, out = null;
    if (args.length >= 3) {
      if (ARG.equals(args[0])) mode = ARG;
      if (ENV.equals(args[0])) mode = ENV;
      if (OUT.equals(args[1])) out = args[2];
    }
    if (mode == null || out == null) {
      System.out.println("usage: " + CommandTestHelper.class.getSimpleName() + " -arg|-env -out file [args...]");
      System.exit(1);
    }

    try (var writer = Files.newBufferedWriter(Path.of(out))) {
      if (mode.equals(ENV)) {
        for (var entry : System.getenv().entrySet()) {
          writer.write(format(entry));
          writer.write('\n');
        }
      }
      else {
        for (int i = 3; i < args.length; i++) {
          writer.write(args[i]);
          writer.write('\n');
        }
      }
    }
  }

  public static String format(Map.Entry<String, String> entry) {
    return entry.getKey() + "=" + entry.getValue().chars().mapToObj(Integer::toHexString).collect(Collectors.joining("_"));
  }
}
