// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.debugger;

import com.intellij.execution.Executor;
import com.intellij.execution.configuration.EmptyRunProfileState;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.PortField;
import com.intellij.util.ThreeState;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.xmlb.SerializationFilter;
import com.intellij.util.xmlb.SkipEmptySerializationFilter;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * @deprecated scriptDebugger.ui is deprecated
 */
@Deprecated
public abstract class RemoteDebugConfiguration extends LocatableConfigurationBase
  implements RunConfigurationWithSuppressedDefaultRunAction, DebuggableRunConfiguration {
  private final int defaultPort;
  private final SerializationFilter serializationFilter = new SkipEmptySerializationFilter() {
    @Override
    protected ThreeState accepts(@NotNull String name, @NotNull Object beanValue) {
      return name.equals("port") ? ThreeState.fromBoolean(!beanValue.equals(defaultPort)) : ThreeState.UNSURE;
    }
  };
  private String host;
  private int port;

  public RemoteDebugConfiguration(Project project, @NotNull ConfigurationFactory factory, String name, int defaultPort) {
    super(project, factory, name);

    port = defaultPort;
    this.defaultPort = defaultPort;
  }

  @Attribute
  public @Nullable String getHost() {
    return host;
  }

  public void setHost(@Nullable String value) {
    if (StringUtil.isEmpty(value) || value.equals("localhost") || value.equals("127.0.0.1")) {
      host = null;
    }
    else {
      host = value;
    }
  }

  @Attribute
  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  @Override
  public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new RemoteDebugConfigurationSettingsEditor();
  }

  @Override
  public @Nullable RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) {
    return EmptyRunProfileState.INSTANCE;
  }

  @Override
  public RunConfiguration clone() {
    RemoteDebugConfiguration configuration = (RemoteDebugConfiguration)super.clone();
    configuration.host = host;
    configuration.port = port;
    return configuration;
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);

    XmlSerializer.deserializeInto(this, element);
    if (port <= 0) {
      port = defaultPort;
    }
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);

    XmlSerializer.serializeInto(this, element, serializationFilter);
  }

  @Override
  public @NotNull InetSocketAddress computeDebugAddress(RunProfileState state) {
    if (host == null) {
      return new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
    }
    else {
      return new InetSocketAddress(host, getPort());
    }
  }

  private final class RemoteDebugConfigurationSettingsEditor extends SettingsEditor<RemoteDebugConfiguration> {
    private final JTextField hostField;
    private final PortField portField;

    RemoteDebugConfigurationSettingsEditor() {
      hostField = GuiUtils.createUndoableTextField();
      portField = new PortField(defaultPort, 1024);
    }

    @Override
    protected void resetEditorFrom(@NotNull RemoteDebugConfiguration configuration) {
      hostField.setText(StringUtil.notNullize(configuration.host, "localhost"));
      portField.setNumber(configuration.port);
    }

    @Override
    protected void applyEditorTo(@NotNull RemoteDebugConfiguration configuration) {
      configuration.setHost(hostField.getText());
      configuration.setPort(portField.getNumber());
    }

    @Override
    protected @NotNull JComponent createEditor() {
      return FormBuilder.createFormBuilder().addLabeledComponent(XDebuggerBundle.message("label.host"), hostField)
        .addLabeledComponent(XDebuggerBundle.message("label.port"), portField).getPanel();
    }
  }
}