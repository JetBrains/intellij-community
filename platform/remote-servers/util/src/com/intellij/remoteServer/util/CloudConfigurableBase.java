package com.intellij.remoteServer.util;

import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author michael.golubev
 */
public abstract class CloudConfigurableBase<SC extends CloudConfigurationBase> implements UnnamedConfigurable {

  protected final SC myConfiguration;

  public CloudConfigurableBase(SC configuration) {
    myConfiguration = configuration;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return getMainPanel();
  }

  @Override
  public boolean isModified() {
    return !getEmailTextField().getText().equals(myConfiguration.getEmail())
           || !new String(getPasswordField().getPassword()).equals(myConfiguration.getPassword());
  }

  @Override
  public void apply() throws ConfigurationException {
    String email = getEmailTextField().getText();
    if (StringUtil.isEmpty(email)) {
      throw new RuntimeConfigurationError("Email required");
    }
    String password = new String(getPasswordField().getPassword());
    if (StringUtil.isEmpty(password)) {
      throw new RuntimeConfigurationError("Password required");
    }

    myConfiguration.setEmail(email);
    myConfiguration.setPassword(password);
  }

  @Override
  public void reset() {
    getEmailTextField().setText(myConfiguration.getEmail());
    getPasswordField().setText(myConfiguration.getPassword());
  }

  @Override
  public void disposeUIResources() {
  }

  protected abstract JComponent getMainPanel();

  protected abstract JTextField getEmailTextField();

  protected abstract JPasswordField getPasswordField();
}
