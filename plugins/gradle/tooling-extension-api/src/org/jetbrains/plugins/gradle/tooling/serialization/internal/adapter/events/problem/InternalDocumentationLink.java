// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem;

import org.gradle.tooling.events.problems.DocumentationLink;
import org.jetbrains.annotations.NotNull;

public class InternalDocumentationLink implements DocumentationLink {

  private final @NotNull String url;

  public InternalDocumentationLink(@NotNull String url) {
    this.url = url;
  }

  @Override
  public @NotNull String getUrl() {
    return url;
  }
}
