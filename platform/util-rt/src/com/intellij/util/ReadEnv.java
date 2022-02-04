// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Map;

public final class ReadEnv {
  public static void main(String[] args) throws Exception {
    @SuppressWarnings("UseOfSystemOutOrSystemErr") Writer out = new BufferedWriter(new OutputStreamWriter(System.out, "UTF-8"));
    try {
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
    finally {
      out.close();
    }
  }
}
