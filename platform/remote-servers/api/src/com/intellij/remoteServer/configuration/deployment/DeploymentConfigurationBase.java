package com.intellij.remoteServer.configuration.deployment;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class DeploymentConfigurationBase<Self extends DeploymentConfigurationBase> extends DeploymentConfiguration implements PersistentStateComponent<Self> {
  @Override
  public PersistentStateComponent<?> getSerializer() {
    return this;
  }

  @Nullable
  @Override
  public Self getState() {
    return (Self)this;
  }

  @Override
  public void loadState(Self state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
