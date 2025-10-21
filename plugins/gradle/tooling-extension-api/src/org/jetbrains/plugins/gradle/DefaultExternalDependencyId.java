// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Objects;

/**
 * @author Vladislav.Soroka
 */
public final class DefaultExternalDependencyId implements ExternalDependencyId, Serializable {
  private static final long serialVersionUID = 1L;

  private String group;
  private String name;
  private String version;
  private @NotNull String packaging = "jar";
  private @Nullable String classifier;

  public DefaultExternalDependencyId() {
  }

  public DefaultExternalDependencyId(String group, String name, String version) {
    this.group = group;
    this.name = name;
    this.version = version;
  }

  public DefaultExternalDependencyId(ExternalDependencyId dependencyId) {
    this(dependencyId.getGroup(), dependencyId.getName(), dependencyId.getVersion());
    packaging = dependencyId.getPackaging();
    classifier = dependencyId.getClassifier();
  }

  @Override
  public String getGroup() {
    return group;
  }

  public void setGroup(String group) {
    this.group = group;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  @Override
  public @NotNull String getPackaging() {
    return packaging;
  }

  public void setPackaging(@NotNull String packaging) {
    this.packaging = packaging;
  }

  @Override
  public @Nullable String getClassifier() {
    return classifier;
  }

  public void setClassifier(@Nullable String classifier) {
    this.classifier = classifier;
  }

  @Override
  public @NotNull String getPresentableName() {
    final StringBuilder buf = new StringBuilder();
    if (group != null) {
      buf.append(group).append(':');
    }
    if(name != null) {
      buf.append(name);
    }
    if (!"jar".equals(packaging)) {
      buf.append(':').append(packaging);
    }
    if (classifier != null) {
      buf.append(':').append(classifier);
    }
    if (version != null) {
      buf.append(':').append(version);
    }
    return buf.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    DefaultExternalDependencyId id = (DefaultExternalDependencyId)o;
    return Objects.equals(group, id.group) &&
           Objects.equals(name, id.name) &&
           Objects.equals(packaging, id.packaging) &&
           Objects.equals(classifier, id.classifier) &&
           Objects.equals(version, id.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(group, name, packaging, classifier, version);
  }

  @Override
  public String toString() {
    return getPresentableName();
  }
}
