// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.nativeplatform.tooling.model.impl;

import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.CompilerDetails;
import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.CppBinary;
import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.CppFileSettings;
import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.LinkerDetails;

import java.io.File;
import java.util.*;

/**
 * @author Vladislav.Soroka
 */
public class CppBinaryImpl implements CppBinary {
  private final String myBaseName;
  private final String myVariantName;
  private final Map<File, CppFileSettings> mySources;
  private final CompilerDetails myCompilerDetails;
  private final LinkerDetails myLinkerDetails;
  private final TargetType myTargetType;

  public CppBinaryImpl(String baseName, String variantName,
                       Map<File, CppFileSettings> sources,
                       CompilerDetails compilerDetails,
                       LinkerDetails linkerDetails,
                       TargetType targetType) {
    myBaseName = baseName;
    myVariantName = variantName;
    mySources = new LinkedHashMap<File, CppFileSettings>(sources);
    myCompilerDetails = compilerDetails;
    myLinkerDetails = linkerDetails;
    myTargetType = targetType;
  }

  public CppBinaryImpl(CppBinary binary) {
    this(binary.getBaseName(), binary.getVariantName(), binary.getSources(),
         new CompilerDetailsImpl(binary.getCompilerDetails()), new LinkerDetailsImpl(binary.getLinkerDetails()), binary.getTargetType());
  }

  @Override
  public String getBaseName() {
    return myBaseName;
  }

  @Override
  public String getVariantName() {
    return myVariantName;
  }

  @Override
  public Map<File, CppFileSettings> getSources() {
    return Collections.unmodifiableMap(mySources);
  }

  @Override
  public CompilerDetails getCompilerDetails() {
    return myCompilerDetails;
  }

  @Override
  public Set<File> getCompileIncludePath() {
    return myCompilerDetails.getIncludePath();
  }

  @Override
  public File getCompilerExecutable() {
    return myCompilerDetails.getExecutable();
  }

  @Override
  public List<String> getCompilerArgs() {
    return myCompilerDetails.getArgs();
  }

  @Override
  public LinkerDetails getLinkerDetails() {
    return myLinkerDetails;
  }

  @Override
  public File getOutputFile() {
    return myLinkerDetails.getOutputFile();
  }

  @Override
  public TargetType getTargetType() {
    return myTargetType;
  }
}
