// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.groovy.compiler.rt;

import java.io.File;

public final class OutputItem {

  public final String outputPath;
  public final String sourcePath;

  public OutputItem(String outputPath, String sourcePath) {
    this.outputPath = outputPath;
    this.sourcePath = sourcePath;
  }

  public OutputItem(File targetDirectory, String outputPath, String sourcePath) {
    this(targetDirectory.getAbsolutePath().replace(File.separatorChar, '/') + '/' + outputPath, sourcePath);
  }

  @Override
  public String toString() {
    return "OutputItem{" +
           "outputPath='" + outputPath + '\'' +
           ", sourcePath='" + sourcePath + '\'' +
           '}';
  }
}
