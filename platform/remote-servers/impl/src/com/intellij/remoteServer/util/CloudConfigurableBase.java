package com.intellij.remoteServer.util;

import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remoteServer.RemoteServerConfigurable;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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
           || !new String(getPasswordField().getPassword()).equals(myConfiguration.getPassword());
  }

  @Override
  public void apply() throws ConfigurationException {
    applyCoreTo(myConfiguration);
  }

  @Override
  public void reset() {
    getEmailTextField().setText(myConfiguration.getEmail());
    getPasswordField().setText(myConfiguration.getPassword());
  }

  protected void applyCoreTo(SC configuration) throws ConfigurationException {
    String email = getEmailTextField().getText();
    if (StringUtil.isEmpty(email)) {
      throw new RuntimeConfigurationError("Email required");
    }
    String password = new String(getPasswordField().getPassword());
    if (StringUtil.isEmpty(password)) {
      throw new RuntimeConfigurationError("Password required");
    }

    configuration.setEmail(email);
    configuration.setPassword(password);
  }

  protected boolean isCoreConfigEqual(SC configuration1, SC configuration2) {
    return Comparing.equal(configuration1.getEmail(), configuration2.getEmail())
           && Comparing.equal(configuration1.getPassword(), configuration2.getPassword());
  }

  private String generateServerName() {
    return UniqueNameGenerator.generateUniqueName(myCloudType.getPresentableName(), new Condition<String>() {

      @Override
      public boolean value(String s) {
        for (RemoteServer<?> server : RemoteServersManager.getInstance().getServers()) {
          if (server.getName().equals(s)) {
            return false;
          }
        }
        return true;
      }
    });
  }

  protected final RemoteServer<SC> createTempServer() {
    RemoteServer<SC> tempServer = RemoteServersManager.getInstance().createServer(myCloudType, generateServerName());
    SC newConfiguration = tempServer.getConfiguration();
    try {
      applyCoreTo(newConfiguration);
    }
    catch (ConfigurationException e) {
      return null;
    }
    return tempServer;
  }

  protected abstract JComponent getMainPanel();

  protected abstract JTextField getEmailTextField();

  protected abstract JPasswordField getPasswordField();
}
