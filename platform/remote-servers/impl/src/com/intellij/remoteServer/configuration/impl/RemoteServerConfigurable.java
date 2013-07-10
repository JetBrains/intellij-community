package com.intellij.remoteServer.configuration.impl;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class RemoteServerConfigurable extends NamedConfigurable<RemoteServer<?>> {
  private final UnnamedConfigurable myConfigurable;
  private final RemoteServer<?> myServer;
  private String myServerName;
  private boolean myNew;

  public <C extends ServerConfiguration> RemoteServerConfigurable(RemoteServer<C> server, Runnable treeUpdater, boolean isNew) {
    super(true, treeUpdater);
    myServer = server;
    myNew = isNew;
    myServerName = myServer.getName();
    C c = server.getConfiguration();
    myConfigurable = server.getType().createConfigurable(c);
  }

  @Override
  public RemoteServer<?> getEditableObject() {
    return myServer;
  }

  @Override
  public String getBannerSlogan() {
    return myServer.getName();
  }

  @Override
  public JComponent createOptionsPanel() {
    JComponent component = myConfigurable.createComponent();
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(component, BorderLayout.NORTH);
    return panel;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return myServerName;
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  public void setDisplayName(String name) {
    myServerName = name;
  }

  @Override
  public boolean isModified() {
    return myNew || myConfigurable.isModified() || !myServerName.equals(myServer.getName());
  }

  @Override
  public void apply() throws ConfigurationException {
    myConfigurable.apply();
    myNew = false;
  }

  @Override
  public void reset() {
    myConfigurable.reset();
  }

  @Override
  public void disposeUIResources() {
    myConfigurable.disposeUIResources();
  }

  @Nullable
  @Override
  public Icon getIcon(boolean expanded) {
    return myServer.getType().getIcon();
  }
}
