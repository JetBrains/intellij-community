/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.model.data;

import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData;
import com.intellij.openapi.externalSystem.model.project.DependencyData;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 11/11/2015
 */
public class EarConfigurationModelData extends AbstractExternalEntityData {
  private static final long serialVersionUID = 1L;

  @NotNull
  public static final Key<EarConfigurationModelData> KEY =
    Key.create(EarConfigurationModelData.class, WebConfigurationModelData.KEY.getProcessingWeight() + 1);

  @NotNull
  private final List<Ear> myEars;
  @NotNull
  private final Collection<DependencyData> myDeployDependencies;
  @NotNull
  private final Collection<DependencyData> myEarlibDependencies;

  public EarConfigurationModelData(@NotNull ProjectSystemId owner,
                                   @NotNull List<Ear> ears,
                                   @NotNull Collection<DependencyData> deployDependencies,
                                   @NotNull Collection<DependencyData> earlibDependencies) {
    super(owner);
    myEars = ears;
    myDeployDependencies = deployDependencies;
    myEarlibDependencies = earlibDependencies;
  }

  @NotNull
  public List<Ear> getEars() {
    return myEars;
  }

  @NotNull
  public Collection<DependencyData> getDeployDependencies() {
    return myDeployDependencies;
  }

  @NotNull
  public Collection<DependencyData> getEarlibDependencies() {
    return myEarlibDependencies;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EarConfigurationModelData)) return false;
    if (!super.equals(o)) return false;

    EarConfigurationModelData data = (EarConfigurationModelData)o;

    if (!myEars.equals(data.myEars)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myEars.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "ears='" + myEars + '\'';
  }
}
