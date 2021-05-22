// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter;

import org.gradle.tooling.model.build.JavaEnvironment;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class InternalJavaEnvironment implements JavaEnvironment, Serializable {
  private final File myJavaHome;
  private final List<String> myJvmArguments;

  public InternalJavaEnvironment(File javaHome, List<String> jvmArguments) {
    myJavaHome = javaHome;
    myJvmArguments = new ArrayList<String>(jvmArguments);
  }

  @Override
  public File getJavaHome() {
    return myJavaHome;
  }

  @Override
  public List<String> getJvmArguments() {
    return myJvmArguments;
  }
}
