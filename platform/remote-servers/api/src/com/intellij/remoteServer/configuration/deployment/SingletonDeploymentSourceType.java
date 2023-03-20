// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private final @Nls String myPresentableName;
  private final SingletonDeploymentSource mySourceInstance;

  public SingletonDeploymentSourceType(@NotNull String id, @NotNull @Nls String name, @NotNull Icon icon) {
    super(id);
    myPresentableName = name;
    mySourceInstance = new SingletonDeploymentSource(icon, getClass());
  }

  protected static <T extends SingletonDeploymentSourceType> T findExtension(@NotNull Class<? extends T> clazz) {
    return DeploymentSourceType.EP_NAME.findExtension(clazz);
  }

  public @NotNull DeploymentSource getSingletonSource() {
    return mySourceInstance;
  }

  @Override
  public void save(@NotNull DeploymentSource source, @NotNull Element tag) {
    //
  }

  @Override
  public @NotNull DeploymentSource load(@NotNull Element tag, @NotNull Project project) {
    return getSingletonSource();
  }

  public final @Nls @NotNull String getPresentableName() {
    return myPresentableName;
  }

  private static class SingletonDeploymentSource implements DeploymentSource {
    private final Class<? extends SingletonDeploymentSourceType> myTypeClass;
    private final Icon myIcon;

    SingletonDeploymentSource(@NotNull Icon icon, @NotNull Class<? extends SingletonDeploymentSourceType> typeClass) {
      myIcon = icon;
      myTypeClass = typeClass;
    }

    @Override
    public final @Nullable File getFile() {
      return null;
    }

    @Override
    public final @Nullable String getFilePath() {
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

    @Override
    public @Nullable Icon getIcon() {
      return myIcon;
    }

    @Override
    public final @NotNull String getPresentableName() {
      return getType().getPresentableName();
    }

    @Override
    public @NotNull SingletonDeploymentSourceType getType() {
      return findExtension(myTypeClass);
    }
  }
}
