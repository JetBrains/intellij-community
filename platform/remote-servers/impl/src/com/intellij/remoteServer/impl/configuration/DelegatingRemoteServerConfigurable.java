/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.remoteServer.impl.configuration;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.remoteServer.RemoteServerConfigurable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
* @author nik
*/
class DelegatingRemoteServerConfigurable extends RemoteServerConfigurable {
  private final UnnamedConfigurable myDelegate;

  public DelegatingRemoteServerConfigurable(UnnamedConfigurable delegate) {
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
