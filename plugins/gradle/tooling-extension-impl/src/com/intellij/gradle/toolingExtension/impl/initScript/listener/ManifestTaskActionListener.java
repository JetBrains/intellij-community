// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.initScript.listener;

import org.gradle.api.tasks.JavaExec;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

@SuppressWarnings("IO_FILE_USAGE")
public class ManifestTaskActionListener extends RunAppTaskActionListener {

  public ManifestTaskActionListener(String taskName) {
    super(taskName);
  }

  @Override
  public File patchTaskClasspath(JavaExec task) throws IOException {
    Manifest manifest = new Manifest();
    Attributes attributes = manifest.getMainAttributes();
    attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
    String classpathFiles = task.getClasspath().getFiles()
      .stream()
      .map(it -> {
        try {
          return it.toURI().toURL().toString();
        }
        catch (MalformedURLException e) {
          throw new RuntimeException(e);
        }
      })
      .collect(Collectors.joining(" "));
    attributes.putValue("Class-Path", classpathFiles);
    File file = File.createTempFile("generated-", "-manifest");
    try (FileOutputStream fos = new FileOutputStream(file);
         JarOutputStream jos = new JarOutputStream(fos, manifest)) {
      jos.putNextEntry(new ZipEntry("META-INF/"));
    }
    task.setClasspath(task.getProject().files(file.getAbsolutePath()));
    return file;
  }
}
