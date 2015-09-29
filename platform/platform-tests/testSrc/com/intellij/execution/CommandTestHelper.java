/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Map;

public class CommandTestHelper {
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

    OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(out), ENC);
    try {
      if (mode == ENV) {
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
    finally {
      writer.close();
    }
  }

  public static String format(Map.Entry<String, String> entry) {
    return entry.getKey() + "=" + entry.getValue().hashCode();
  }
}