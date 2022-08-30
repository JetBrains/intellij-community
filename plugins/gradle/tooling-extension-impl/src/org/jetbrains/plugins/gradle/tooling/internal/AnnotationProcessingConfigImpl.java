// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.internal;

import org.gradle.internal.impldep.com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.AnnotationProcessingConfig;
import org.jetbrains.plugins.gradle.model.ExternalDependency;
import org.jetbrains.plugins.gradle.tooling.util.GradleContainerUtil;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AnnotationProcessingConfigImpl implements AnnotationProcessingConfig, Serializable {

  private final List<ExternalDependency> myDependencies;
  private final List<String> myArgs;
  private final String myProcessorOutput;
  private final boolean isTestSources;

  public AnnotationProcessingConfigImpl(List<String> args,
                                        String output,
                                        boolean sources,
                                        Collection<ExternalDependency> dependencies) {
    myProcessorOutput = output;
    isTestSources = sources;
    myArgs = args;
    myDependencies = new ArrayList<>(dependencies);
  }

  @NotNull
  @Override
  public Collection<ExternalDependency> annotationProcessors() {
    return myDependencies;
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
           Objects.equal(myDependencies, config.myDependencies) &&
           Objects.equal(myArgs, config.myArgs);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myArgs, myDependencies, myProcessorOutput, isTestSources);
  }
}
