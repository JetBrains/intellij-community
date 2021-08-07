// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;

public class Cat {
  public static void main(String[] args) throws IOException {
    String fileName = args[0];
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(fileName), StandardCharsets.UTF_8))) {
      StringBuilder content = new StringBuilder();
      reader.lines().forEach(content::append);
      // Breakpoint! suspendPolicy(SuspendNone) LogExpression("Debugger: " + content)
      System.out.print(content);
    }
  }
}