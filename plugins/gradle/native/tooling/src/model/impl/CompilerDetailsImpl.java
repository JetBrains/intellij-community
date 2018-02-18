// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.nativeplatform.tooling.model.impl;

import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.CompilerDetails;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 */
public class CompilerDetailsImpl implements CompilerDetails {

  private final Set<File> myIncludePath;
  private final Set<File> mySystemIncludes;
  private final String myCompileTaskName;
  private File myExecutable;
  private final List<String> myArgs;

  public CompilerDetailsImpl(String compileTaskName,
                             File executable,
                             List<String> args,
                             Set<File> includePath,
                             Set<File> systemIncludes) {
    myCompileTaskName = compileTaskName;
    myExecutable = executable;
    myArgs = args;
    myIncludePath = new LinkedHashSet<File>(includePath);
    mySystemIncludes = new LinkedHashSet<File>(systemIncludes);
  }

  @Override
  public String getCompileTaskName() {
    return myCompileTaskName;
  }

  @Override
  public Set<File> getIncludePath() {
    return Collections.unmodifiableSet(myIncludePath);
  }

  @Override
  public Set<File> getSystemIncludes() {
    return Collections.unmodifiableSet(mySystemIncludes);
  }

  @Override
  public File getExecutable() {
    return myExecutable;
  }

  @Override
  public List<String> getArgs() {
    return Collections.unmodifiableList(myArgs);
  }
}
