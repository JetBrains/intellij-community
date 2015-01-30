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
package com.intellij.notification.impl;

import com.intellij.notification.impl.ui.NotificationsConfigurablePanel;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author spleaner
 */
public class NotificationsConfigurable implements Configurable, SearchableConfigurable, Configurable.NoScroll {
  public static final String DISPLAY_NAME = "Notifications";
  static final String ID = "reference.settings.ide.settings.notifications";
  private NotificationsConfigurablePanel myComponent;

  @Override
  @Nls
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  @NotNull
  public String getHelpTopic() {
    return ID;
  }

  @Override
  public JComponent createComponent() {
    if (myComponent == null) {
      myComponent = new NotificationsConfigurablePanel();
    }

    return myComponent;
  }

  @Override
  public boolean isModified() {
    return myComponent != null && myComponent.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    myComponent.apply();
  }

  @Override
  public void reset() {
    myComponent.reset();
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myComponent);
    myComponent = null;
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @Override
  public Runnable enableSearch(final String option) {
    return new Runnable() {
      @Override
      public void run() {
        myComponent.selectGroup(option);
      }
    };
  }
}
