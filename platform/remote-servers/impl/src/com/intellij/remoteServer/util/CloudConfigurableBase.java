// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.util;

import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remoteServer.CloudBundle;
import com.intellij.remoteServer.RemoteServerConfigurable;
import com.intellij.remoteServer.ServerType;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

/**
 * @author michael.golubev
 */
public abstract class CloudConfigurableBase<SC extends CloudConfigurationBase> extends RemoteServerConfigurable {

  private final ServerType<SC> myCloudType;
  protected final SC myConfiguration;

  public CloudConfigurableBase(ServerType<SC> cloudType, SC configuration) {
    myCloudType = cloudType;
    myConfiguration = configuration;
  }

  protected final ServerType<SC> getCloudType() {
    return myCloudType;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return getMainPanel();
  }

  @Override
  public boolean isModified() {
    return !getEmailTextField().getText().equals(myConfiguration.getEmail())
           || !new String(getPasswordField().getPassword()).equals(myConfiguration.getPasswordSafe())
           || !myConfiguration.isPasswordSafe();
  }

  @Override
  public void apply() throws ConfigurationException {
    applyCoreTo(myConfiguration, false);
  }

  @Override
  public void reset() {
    getEmailTextField().setText(myConfiguration.getEmail());
    getPasswordField().setText(myConfiguration.getPasswordSafe());
  }

  protected void applyCoreTo(SC configuration) throws ConfigurationException {
    applyCoreTo(configuration, false);
  }

  protected void applyCoreTo(SC configuration, boolean forComparison) throws ConfigurationException {
    String email = getEmailTextField().getText();
    if (StringUtil.isEmpty(email)) {
      throw new RuntimeConfigurationError(CloudBundle.message("dialog.message.email.required"));
    }
    String password = new String(getPasswordField().getPassword());
    if (StringUtil.isEmpty(password)) {
      throw new RuntimeConfigurationError(CloudBundle.message("dialog.message.password.required"));
    }

    configuration.setEmail(email);
    if (forComparison) {
      configuration.setPassword(password);
    }
    else {
      configuration.setPasswordSafe(password);
    }
  }

  protected boolean isCoreConfigEqual(SC configuration1, SC configuration2) {
    return Objects.equals(configuration1.getEmail(), configuration2.getEmail())
           && Objects.equals(configuration1.getPasswordSafe(), configuration2.getPasswordSafe());
  }

  protected abstract JComponent getMainPanel();

  protected abstract JTextField getEmailTextField();

  protected abstract JPasswordField getPasswordField();
}
