// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.initScript.listener;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.JavaExec;
import org.gradle.process.CommandLineArgumentProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@SuppressWarnings("IO_FILE_USAGE")
public class ClasspathFileTaskActionListener extends RunAppTaskActionListener {

  private final String myMainClass;
  private final String myIntelliJRtPath;

  public ClasspathFileTaskActionListener(String taskName, String mainClass, String intelliJRtPath) {
    super(taskName);
    myMainClass = mainClass;
    myIntelliJRtPath = intelliJRtPath;
  }

  @Override
  public File patchTaskClasspath(JavaExec task) throws IOException {
    File file = File.createTempFile("generated-", "-classpathFile");
    try (
      FileOutputStream fos = new FileOutputStream(file);
      OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
      PrintWriter writer = new PrintWriter(osw)
    ) {
      FileCollection classpath = task.getClasspath();
      Set<File> classpathFiles = classpath.getFiles();
      classpathFiles.forEach(f -> writer.println(f.getPath()));
    }
    List<String> args = new ArrayList<>();
    args.add(file.getAbsolutePath());
    args.add(myMainClass);
    args.addAll(task.getArgs());

    task.setArgs(new ArrayList<>());
    task.getArgumentProviders().add(new CommandLineArgumentProvider() {
      @Override
      public Iterable<String> asArguments() {
        return args;
      }
    });
    task.setProperty("main", "com.intellij.rt.execution.CommandLineWrapper");
    task.setClasspath(task.getProject().files(Arrays.asList(myIntelliJRtPath)));
    return file;
  }
}
