// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.data;

import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData;
import com.intellij.openapi.externalSystem.model.project.DependencyData;
import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class EarConfigurationModelData extends AbstractExternalEntityData implements ArtifactConfiguration {
  public static final @NotNull Key<EarConfigurationModelData> KEY =
    Key.create(EarConfigurationModelData.class, WebConfigurationModelData.KEY.getProcessingWeight() + 1);

  private final @NotNull List<Ear> ears;
  private final @NotNull Collection<DependencyData<?>> deployDependencies;
  private final @NotNull Collection<DependencyData<?>> earlibDependencies;

  @PropertyMapping({"owner", "ears", "deployDependencies", "earlibDependencies"})
  public EarConfigurationModelData(@NotNull ProjectSystemId owner,
                                   @NotNull List<Ear> ears,
                                   @NotNull Collection<DependencyData<?>> deployDependencies,
                                   @NotNull Collection<DependencyData<?>> earlibDependencies) {
    super(owner);

    this.ears = ears;
    this.deployDependencies = deployDependencies;
    this.earlibDependencies = earlibDependencies;
  }

  @SuppressWarnings("unused")
  private EarConfigurationModelData() {
    super(ProjectSystemId.IDE);

    ears = new ArrayList<>();
    deployDependencies = new ArrayList<>();
    earlibDependencies = new ArrayList<>();
  }

  @Override
  public @NotNull List<Ear> getArtifacts() {
    return ears;
  }

  public @NotNull Collection<DependencyData<?>> getDeployDependencies() {
    return deployDependencies;
  }

  public @NotNull Collection<DependencyData<?>> getEarlibDependencies() {
    return earlibDependencies;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EarConfigurationModelData data)) return false;
    if (!super.equals(o)) return false;

    if (!ears.equals(data.ears)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + ears.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "ears='" + ears + '\'';
  }
}
