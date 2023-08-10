// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.internal;

import org.gradle.internal.impldep.com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.AnnotationProcessingConfig;
import org.jetbrains.plugins.gradle.tooling.util.GradleContainerUtil;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AnnotationProcessingConfigImpl implements AnnotationProcessingConfig, Serializable {
  private final List<File> myPaths;
  private final List<String> myArgs;
  private final String myProcessorOutput;
  private final boolean isTestSources;

  public AnnotationProcessingConfigImpl(Collection<File> files, List<String> args, String output, boolean sources) {
    myProcessorOutput = output;
    isTestSources = sources;
    myPaths = new ArrayList<>(files);
    myArgs = args;
  }

  @NotNull
  @Override
  public Collection<String> getAnnotationProcessorPath() {
    return GradleContainerUtil.unmodifiablePathSet(myPaths);
  }

  @NotNull
  @Override
  public Collection<String> getAnnotationProcessorArguments() {
    return myArgs;
  }
  @Override
  public boolean isTestSources() {
    return isTestSources;
  }

  @Nullable
  @Override
  public String getProcessorOutput() {
    return myProcessorOutput;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AnnotationProcessingConfigImpl config = (AnnotationProcessingConfigImpl)o;
    return isTestSources == config.isTestSources &&
           Objects.equal(myProcessorOutput, config.myProcessorOutput) &&
           Objects.equal(myPaths, config.myPaths) &&
           Objects.equal(myArgs, config.myArgs);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myPaths, myArgs, myProcessorOutput, isTestSources);
  }
}
