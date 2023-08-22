// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.openapi.util.Disposer;
import com.intellij.remoteServer.CloudBundle;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.impl.configuration.RemoteServerConnectionTester;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

public class RemoteServerComboWithAutoDetect<S extends ServerConfiguration> extends RemoteServerCombo<S> {
  private AutoDetectedItem myAutoDetectedItem;

  public RemoteServerComboWithAutoDetect(@NotNull ServerType<S> serverType) {
    super(serverType);
  }

  @NotNull
  @Override
  protected ServerItem getNoServersItem() {
    return getServerType().canAutoDetectConfiguration() ? findOrCreateAutoDetectedItem() : super.getNoServersItem();
  }

  protected AutoDetectedItem findOrCreateAutoDetectedItem() {
    if (myAutoDetectedItem == null) {
      myAutoDetectedItem = new AutoDetectedItem();
    }
    return myAutoDetectedItem;
  }

  public void validateAutoDetectedItem() throws RuntimeConfigurationException {
    if (myAutoDetectedItem != null && myAutoDetectedItem == getSelectedItem()) {
      myAutoDetectedItem.validateConnection();
    }
  }

  private enum TestConnectionState {
    INITIAL {
      @Override
      public void validateConnection() throws RuntimeConfigurationException {
        //
      }
    },
    IN_PROGRESS {
      @Override
      public void validateConnection() throws RuntimeConfigurationException {
        throw new RuntimeConfigurationWarning(CloudBundle.message("remote.server.combo.message.test.connection.in.progress"));
      }
    },
    SUCCESSFUL {
      @Override
      public void validateConnection() throws RuntimeConfigurationException {
        //
      }
    },
    FAILED {
      @Override
      public void validateConnection() throws RuntimeConfigurationException {
        throw new RuntimeConfigurationError(
          CloudBundle.message("remote.server.combo.message.test.connection.failed")/*, () -> createAndEditNewServer()*/);
      }
    };

    public abstract void validateConnection() throws RuntimeConfigurationException;
  }

  private class AutoDetectedItem extends RemoteServerCombo.ServerItemImpl {
    private final AtomicReference<TestConnectionState> myTestConnectionStateA = new AtomicReference<>(TestConnectionState.INITIAL);
    private volatile RemoteServer<S> myServerInstance;
    private volatile long myLastStartedTestConnectionMillis = -1;

    AutoDetectedItem() {
      super(null);
    }

    @Override
    public void render(@NotNull SimpleColoredComponent ui) {
      ui.setIcon(getServerType().getIcon());

      boolean failed = myTestConnectionStateA.get() == TestConnectionState.FAILED;
      ui.append(CloudBundle.message("remote.server.combo.auto.detected.server", getServerType().getPresentableName()),
                failed ? SimpleTextAttributes.ERROR_ATTRIBUTES : SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
    }

    public void validateConnection() throws RuntimeConfigurationException {
      myTestConnectionStateA.get().validateConnection();
    }

    @Override
    public void onBrowseAction() {
      createAndEditNewServer();
    }

    @Override
    public void onItemChosen() {
      if (myServerInstance == null) {
        myServerInstance = RemoteServersManager.getInstance().createServer(getServerType());
        RemoteServerConnectionTester tester = new RemoteServerConnectionTester(myServerInstance);
        setTestConnectionState(TestConnectionState.IN_PROGRESS);
        myLastStartedTestConnectionMillis = System.currentTimeMillis();
        tester.testConnection(this::connectionTested);
      }
    }

    @Nullable
    @Override
    public String getServerName() {
      return null;
    }

    @Nullable
    @Override
    public RemoteServer<S> findRemoteServer() {
      return myServerInstance;
    }

    private void setTestConnectionState(@NotNull TestConnectionState state) {
      boolean changed = myTestConnectionStateA.getAndSet(state) != state;
      if (changed) {
        UIUtil.invokeLaterIfNeeded(RemoteServerComboWithAutoDetect.this::fireStateChanged);
      }
    }

    private void connectionTested(boolean wasConnected, @SuppressWarnings("unused") String errorStatus) {
      assert myLastStartedTestConnectionMillis > 0;
      waitABit(2000);

      if (wasConnected) {
        setTestConnectionState(TestConnectionState.SUCCESSFUL);
        UIUtil.invokeLaterIfNeeded(() -> {
          if (!Disposer.isDisposed(RemoteServerComboWithAutoDetect.this)) {
            assert myServerInstance != null;
            RemoteServersManager.getInstance().addServer(myServerInstance);
            refillModel(myServerInstance);
          }
          myServerInstance = null;
        });
      }
      else {
        setTestConnectionState(TestConnectionState.FAILED);
        myServerInstance = null;
      }
    }

    /**
     * Too quick validation just flickers the screen, so we will ensure that validation message is shown for at least some time
     */
    private void waitABit(@SuppressWarnings("SameParameterValue") long maxTotalDelayMillis) {
      final long THRESHOLD_MS = 50;
      long naturalDelay = System.currentTimeMillis() - myLastStartedTestConnectionMillis;
      if (naturalDelay > 0 && naturalDelay + THRESHOLD_MS < maxTotalDelayMillis) {
        try {
          Thread.sleep(maxTotalDelayMillis - naturalDelay - THRESHOLD_MS);
        }
        catch (InterruptedException ignored) {
          //
        }
      }
      myLastStartedTestConnectionMillis = -1;
    }
  }
}
