// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Map;

public final class CommandTestHelper {
  public static final String ARG = "-arg";
  public static final String ENV = "-env";
  public static final String OUT = "-out";
  public static final String ENC = "UTF-8";

  public static void main(String[] args) throws IOException {
    String mode = null;
    String out = null;
    if (args.length >= 3) {
      if (ARG.equals(args[0])) mode = ARG;
      if (ENV.equals(args[0])) mode = ENV;
      if (OUT.equals(args[1])) out = args[2];
    }
    if (mode == null || out == null) {
      System.out.println("usage: " + CommandTestHelper.class.getSimpleName() + " -arg|-env -out file [args...]");
      System.exit(1);
    }

    try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(out), ENC)) {
      if (mode.equals(ENV)) {
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
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
    return entry.getKey() + "=" + entry.getValue().hashCode();
  }
}