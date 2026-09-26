// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.dsl;

import org.gradle.tooling.model.dsl.KotlinDslBaseScriptModel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.List;

@ApiStatus.Internal
@SuppressWarnings("IO_FILE_USAGE")
public class InternalKotlinDslBaseScriptModel implements KotlinDslBaseScriptModel, Serializable {

  private final @NotNull List<File> scriptTemplatesClassPath;
  private final @NotNull List<File> compileClassPath;
  private final @NotNull List<String> implicitImports;
  private final @NotNull List<String> templateClassNames;

  public InternalKotlinDslBaseScriptModel(
    @NotNull List<File> scriptTemplatesClassPath,
    @NotNull List<File> compileClassPath,
    @NotNull List<String> implicitImports,
    @NotNull List<String> templateClassNames
  ) {
    this.scriptTemplatesClassPath = scriptTemplatesClassPath;
    this.compileClassPath = compileClassPath;
    this.implicitImports = implicitImports;
    this.templateClassNames = templateClassNames;
  }

  @Override
  public @NotNull List<File> getScriptTemplatesClassPath() {
    return scriptTemplatesClassPath;
  }

  @Override
  public @NotNull List<File> getCompileClassPath() {
    return compileClassPath;
  }

  @Override
  public @NotNull List<String> getImplicitImports() {
    return implicitImports;
  }

  @Override
  public @NotNull List<String> getTemplateClassNames() {
    return templateClassNames;
  }
}
