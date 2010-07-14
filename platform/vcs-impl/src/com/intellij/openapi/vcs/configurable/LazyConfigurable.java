/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.util.Getter;

import javax.swing.*;
import java.awt.*;

public class LazyConfigurable implements UnnamedConfigurable {
  private final Getter<Configurable> myGetter;
  private Configurable myDelegate;
  private final JPanel myParentPanel;

  public LazyConfigurable(Getter<Configurable> getter, JPanel parentPanel) {
    myGetter = getter;
    myParentPanel = parentPanel;
    myParentPanel.setLayout(new BorderLayout());
    myParentPanel.removeAll();
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myDelegate != null) {
      myDelegate.apply();
    }
  }
  @Override
  public JComponent createComponent() {
    if (myDelegate == null) {
      myDelegate = myGetter.get();
      myParentPanel.add(myDelegate.createComponent(), BorderLayout.CENTER);
    }
    return myParentPanel;
  }
  @Override
  public boolean isModified() {
    if (myDelegate != null) {
      return myDelegate.isModified();
    }
    return false;
  }
  @Override
  public void reset() {
    if (myDelegate != null) {
      myDelegate.reset();
    }
  }
  @Override
  public void disposeUIResources() {
    if (myDelegate != null) {
      myDelegate.disposeUIResources();
    }
  }

  public JPanel getParentPanel() {
    return myParentPanel;
  }
}
