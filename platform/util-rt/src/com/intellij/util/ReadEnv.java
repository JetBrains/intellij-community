// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Map;

public class ReadEnv {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) throw new Exception("Exactly one argument expected");

    Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[0]), "UTF-8"));
    try {
      for (Map.Entry<String, String> each : System.getenv().entrySet()) {
        if (each.getKey().startsWith("=")) continue;
        out.write(each.getKey() + "=" + each.getValue() + "\0");
      }
    }
    finally {
      out.close();
    }
  }
}
