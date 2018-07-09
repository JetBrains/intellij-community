// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.nativeplatform.tooling.model;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 */
public interface CppBinary extends Serializable {
  /**
   * Returns the base name of this component. This is used by Gradle to calculate output file names.
   */
  String getBaseName();

  /**
   * Returns the binary variant name, e.g. Debug, Release etc.
   */
  String getVariantName();

  Set<File> getSources();

  CompilerDetails getCompilerDetails();

  Set<File> getCompileIncludePath();

  File getCompilerExecutable();

  List<String> getCompilerArgs();

  LinkerDetails getLinkerDetails();

  File getOutputFile();

  TargetType getTargetType();

  enum TargetType {
    EXECUTABLE,
    STATIC_LIBRARY,
    SHARED_LIBRARY
  }
}
