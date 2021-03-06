// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter;

import org.gradle.tooling.model.gradle.GradleScript;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;

@ApiStatus.Internal
public final class InternalGradleScript implements GradleScript {
  private File sourceFile;

  @Override
  public File getSourceFile() {
    return this.sourceFile;
  }

  public void setSourceFile(File sourceFile) {
    this.sourceFile = sourceFile;
  }
}
