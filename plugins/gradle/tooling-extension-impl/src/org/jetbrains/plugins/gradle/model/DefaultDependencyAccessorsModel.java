// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class DefaultDependencyAccessorsModel implements DependencyAccessorsModel {
  private static final long serialVersionUID = 1L;

  private final List<String> sources;

  private final List<String> classes;

  @PropertyMapping({"sources", "classes"})
  public DefaultDependencyAccessorsModel(@NotNull List<String> sources, @NotNull List<String> classes) {
    this.sources = sources;
    this.classes = classes;
  }

  @Override
  public List<String> getSources() {
    return sources;
  }

  @Override
  public List<String> getClasses() {
    return classes;
  }
}
