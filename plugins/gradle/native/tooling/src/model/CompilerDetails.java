// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.nativeplatform.tooling.model;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 */
public interface CompilerDetails extends Serializable {
  String getCompileTaskName();

  String getCompilerKind();

  Set<File> getIncludePath();

  Set<File> getSystemIncludes();

  File getExecutable();

  File getWorkingDir();

  List<String> getArgs();
}
