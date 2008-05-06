/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject.config.ui.configurables;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.NamedConfigurable;
import org.intellij.plugins.intelliLang.inject.config.Injection;
import org.intellij.plugins.intelliLang.inject.config.ui.InjectionPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class InjectionConfigurable<T extends Injection, P extends InjectionPanel<T>> extends NamedConfigurable<T> {
  private final Runnable myTreeUpdater;
  protected final T myInjection;
  protected final Project myProject;
  private P myPanel;

  public InjectionConfigurable(T injection, Runnable treeUpdater, Project project) {
    myProject = project;
    myInjection = injection;
    myTreeUpdater = treeUpdater;
  }

  public void setDisplayName(String name) {
  }

  public T getEditableObject() {
    return myInjection;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  public JComponent createOptionsPanel() {
    myPanel = createOptionsPanelImpl();
    myPanel.addUpdater(myTreeUpdater);
    return myPanel.getComponent();
  }

  protected abstract P createOptionsPanelImpl();

  public P getPanel() {
    return myPanel;
  }

  public boolean isModified() {
    return myPanel.isModified();
  }

  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  public void reset() {
    myPanel.reset();
  }

  public void disposeUIResources() {
    myPanel = null;
  }

  public String getDisplayName() {
    final P p = getPanel();
    return p != null ? p.getInjection().getDisplayName() : myInjection.getDisplayName();
  }
}
