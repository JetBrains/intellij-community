// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.dsl;

import org.gradle.tooling.model.dsl.GradleDslBaseScriptModel;
import org.gradle.tooling.model.dsl.GroovyDslBaseScriptModel;
import org.gradle.tooling.model.dsl.KotlinDslBaseScriptModel;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public class InternalGradleDslBaseScriptModel implements GradleDslBaseScriptModel, Serializable {

  private final @NotNull InternalGroovyDslBaseScriptModel groovyModel;
  private final @NotNull InternalKotlinDslBaseScriptModel kotlinModel;

  public InternalGradleDslBaseScriptModel(
    @NotNull InternalGroovyDslBaseScriptModel groovyModel,
    @NotNull InternalKotlinDslBaseScriptModel kotlinModel
  ) {
    this.groovyModel = groovyModel;
    this.kotlinModel = kotlinModel;
  }

  @Override
  public @NotNull GroovyDslBaseScriptModel getGroovyDslBaseScriptModel() {
    return groovyModel;
  }

  @Override
  public @NotNull KotlinDslBaseScriptModel getKotlinDslBaseScriptModel() {
    return kotlinModel;
  }

  public static @NotNull GradleDslBaseScriptModel convertDslBaseScriptModel(@NotNull GradleDslBaseScriptModel model) {
    GroovyDslBaseScriptModel groovyModel = model.getGroovyDslBaseScriptModel();
    KotlinDslBaseScriptModel kotlinModel = model.getKotlinDslBaseScriptModel();
    return new InternalGradleDslBaseScriptModel(
      new InternalGroovyDslBaseScriptModel(
        groovyModel.getCompileClassPath(),
        groovyModel.getImplicitImports()
      ),
      new InternalKotlinDslBaseScriptModel(
        kotlinModel.getScriptTemplatesClassPath(),
        kotlinModel.getCompileClassPath(),
        kotlinModel.getImplicitImports(),
        kotlinModel.getTemplateClassNames()
      )
    );
  }
}
