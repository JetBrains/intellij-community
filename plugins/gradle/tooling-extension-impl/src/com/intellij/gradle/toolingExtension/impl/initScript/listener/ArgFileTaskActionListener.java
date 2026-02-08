// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.initScript.listener;

import org.gradle.api.tasks.JavaExec;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

@SuppressWarnings("IO_FILE_USAGE")
public class ArgFileTaskActionListener extends RunAppTaskActionListener {

  public ArgFileTaskActionListener(String taskName) {
    super(taskName);
  }

  @Override
  @SuppressWarnings({"IO_FILE_USAGE", "SSBasedInspection"})
  public File patchTaskClasspath(JavaExec task) throws IOException {
    File file = File.createTempFile("generated-", "-argFile");
    try (
      OutputStream os = Files.newOutputStream(file.toPath());
      OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
      PrintWriter writer = new PrintWriter(osw)
    ) {
      String lineSep = System.getProperty("line.separator");
      writer.print("-classpath" + lineSep);
      writer.print(quoteArg(task.getClasspath().getAsPath()));
      writer.print(lineSep);
    }
    task.jvmArgs("@" + file.getAbsolutePath());
    task.setClasspath(task.getProject().files(new ArrayList<>()));
    return file;
  }

  private static String quoteArg(String arg) {
    String specials = " #'\"\n\r\t\f";
    boolean hasSpecialCharacters = specials.chars()
      .anyMatch(characterCode -> arg.indexOf(characterCode) != -1);
    if (!hasSpecialCharacters) {
      return arg;
    }
    StringBuilder sb = new StringBuilder(arg.length() * 2);
    for (int i = 0; i < arg.length(); i++) {
      char c = arg.charAt(i);
      if (c == ' ' || c == '#' || c == '\'') {
        sb.append('"').append(c).append('"');
      }
      else if (c == '"') {
        sb.append("\"\\\"\"");
      }
      else if (c == '\n') {
        sb.append("\"\\n\"");
      }
      else if (c == '\r') {
        sb.append("\"\\r\"");
      }
      else if (c == '\t') {
        sb.append("\"\\t\"");
      }
      else if (c == '\f') {
        sb.append("\"\\f\"");
      }
      else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
