// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public class DefaultGradleProperty implements GradleProperty {
  private static final long serialVersionUID = 1L;

  @NotNull
  private final String name;
  @NotNull
  private final String typeFqn;

  @PropertyMapping({"name", "typeFqn"})
  public DefaultGradleProperty(@NotNull String name, @Nullable String typeFqn) {
    this.name = name;
    this.typeFqn = typeFqn == null ? "java.lang.Object" : typeFqn;
  }

  public DefaultGradleProperty(GradleProperty property) {
    this(property.getName(), property.getTypeFqn());
  }

  @NotNull
  @Override
  public String getName() {
    return name;
  }

  @NotNull
  @Override
  public String getTypeFqn() {
    return typeFqn;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DefaultGradleProperty property = (DefaultGradleProperty)o;

    if (!name.equals(property.name)) return false;
    if (!typeFqn.equals(property.typeFqn)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = name.hashCode();
    result = 31 * result + typeFqn.hashCode();
    return result;
  }
}
