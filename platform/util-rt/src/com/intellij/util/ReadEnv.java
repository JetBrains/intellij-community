// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus.Internal;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

@Internal
public final class ReadEnv {
  public static void main(String[] args) throws Exception {
    try (Writer out = new BufferedWriter(createWriter(args))) {
      for (Map.Entry<String, String> each : System.getenv().entrySet()) {
        // On Windows, the environment may include variables that start with '=' (https://stackoverflow.com/questions/30102750).
        // Such variables break the output parser and are unimportant, hence are filtered out.
        if (each.getKey().startsWith("=")) continue;

        out.write(each.getKey());
        out.write('=');
        out.write(each.getValue());
        out.write('\0');
      }
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static OutputStreamWriter createWriter(String[] args) throws IOException {
    if (args.length > 0) {
      return new OutputStreamWriter(Files.newOutputStream(Paths.get(args[0])), StandardCharsets.UTF_8);
    }
    else {
      return new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
    }
  }
}
