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
  private final String myCompileKind;
  private final File myExecutable;
  private final File myWorkingDir;
  private final List<String> myArgs;

  public CompilerDetailsImpl(String compileKind,
                             String compileTaskName,
                             File executable,
                             File workingDir,
                             List<String> args,
                             Set<File> includePath,
                             Set<File> systemIncludes) {
    myCompileKind = compileKind;
    myCompileTaskName = compileTaskName;
    myExecutable = executable;
    myWorkingDir = workingDir;
    myArgs = args;
    myIncludePath = new LinkedHashSet<File>(includePath);
    mySystemIncludes = new LinkedHashSet<File>(systemIncludes);
  }

  public CompilerDetailsImpl(CompilerDetails details) {
    this(details.getCompilerKind(), details.getCompileTaskName(), details.getExecutable(), details.getWorkingDir(), details.getArgs(),
         details.getIncludePath(), details.getSystemIncludes());
  }

  @Override
  public String getCompileTaskName() {
    return myCompileTaskName;
  }

  @Override
  public String getCompilerKind() {
    return myCompileKind;
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
  public File getWorkingDir() {
    return myWorkingDir;
  }

  @Override
  public List<String> getArgs() {
    return Collections.unmodifiableList(myArgs);
  }
}
