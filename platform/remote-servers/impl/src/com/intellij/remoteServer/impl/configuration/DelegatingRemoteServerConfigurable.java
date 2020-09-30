// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.configuration;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.remoteServer.RemoteServerConfigurable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class DelegatingRemoteServerConfigurable extends RemoteServerConfigurable {
  private final UnnamedConfigurable myDelegate;

  DelegatingRemoteServerConfigurable(UnnamedConfigurable delegate) {
    myDelegate = delegate;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myDelegate.createComponent();
  }

  @Override
  public boolean isModified() {
    return myDelegate.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    myDelegate.apply();
  }

  @Override
  public void reset() {
    myDelegate.reset();
  }
}
