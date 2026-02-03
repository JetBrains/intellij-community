// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.groovy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.JavaBuilderExtension;

import java.io.File;

public final class GroovyJavaBuilderExtension extends JavaBuilderExtension {
  @Override
  public boolean shouldHonorFileEncodingForCompilation(@NotNull File file) {
    return GroovyBuilder.isGroovyFile(file.getAbsolutePath());
  }
}
