/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.remoteServer.configuration.deployment;

import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

/**
 * There may be only a single instance of the deployment source of this type, all the configuration bits are stored in the
 * {@link DeploymentConfiguration}.
 * <p/>
 * Deployment sources of this type are excluded from the choice of the deployment sources in the "generic" deployment run configuration.
 * Instead, deployment configurations for this particular type will be managed by ad hoc
 * {@link com.intellij.execution.configurations.ConfigurationFactory}. Thus, user will not be able to switch from
 * {@link SingletonDeploymentSourceType} to any other deployment source without recreating of the configuration.
 */
public class SingletonDeploymentSourceType extends DeploymentSourceType<DeploymentSource> {
  @Nls
  private final String myPresentableName;
  private final SingletonDeploymentSource mySourceInstance;

  public SingletonDeploymentSourceType(@NotNull String id, @NotNull @Nls String name, @NotNull Icon icon) {
    super(id);
    myPresentableName = name;
    mySourceInstance = new SingletonDeploymentSource(icon, getClass());
  }

  protected static <T extends SingletonDeploymentSourceType> T findExtension(@NotNull Class<? extends T> clazz) {
    return DeploymentSourceType.EP_NAME.findExtension(clazz);
  }

  @NotNull
  public DeploymentSource getSingletonSource() {
    return mySourceInstance;
  }

  @Override
  public void save(@NotNull DeploymentSource source, @NotNull Element tag) {
    //
  }

  @NotNull
  @Override
  public DeploymentSource load(@NotNull Element tag, @NotNull Project project) {
    return getSingletonSource();
  }

  @Nls
  @NotNull
  public final String getPresentableName() {
    return myPresentableName;
  }

  private static class SingletonDeploymentSource implements DeploymentSource {
    private final Class<? extends SingletonDeploymentSourceType> myTypeClass;
    private final Icon myIcon;

    SingletonDeploymentSource(@NotNull Icon icon, @NotNull Class<? extends SingletonDeploymentSourceType> typeClass) {
      myIcon = icon;
      myTypeClass = typeClass;
    }

    @Nullable
    @Override
    public final File getFile() {
      return null;
    }

    @Nullable
    @Override
    public final String getFilePath() {
      return null;
    }

    @Override
    public final boolean isArchive() {
      return false;
    }

    @Override
    public final boolean isValid() {
      return true;
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return myIcon;
    }

    @NotNull
    @Override
    public final String getPresentableName() {
      return getType().getPresentableName();
    }

    @NotNull
    @Override
    public SingletonDeploymentSourceType getType() {
      return findExtension(myTypeClass);
    }
  }
}
