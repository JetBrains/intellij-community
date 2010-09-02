/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author Eugene Belyaev & Eugene Zhuravlev
 */
public class DebuggerConfigurable implements SearchableConfigurable.Parent {
  public static final String DISPLAY_NAME = XDebuggerBundle.message("debugger.configurable.display.name");
  private Configurable myRootConfigurable;
  private Configurable[] myChildren;

  public DebuggerConfigurable(Configurable rootConfigurable, List<Configurable> children) {
    myRootConfigurable = rootConfigurable;
    myChildren = children.toArray(new Configurable[children.size()]);
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableDebugger.png");
  }

  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  public String getHelpTopic() {
    return myRootConfigurable != null? myRootConfigurable.getHelpTopic() : null;
  }

  public Configurable[] getConfigurables() {
    return myChildren;
  }

  public void apply() throws ConfigurationException {
    for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
      support.getSettingsPanelProvider().apply();
    }
    if (myRootConfigurable != null) {
      myRootConfigurable.apply();
    }
  }

  public boolean hasOwnContent() {
    return myRootConfigurable != null;
  }

  public boolean isVisible() {
    return true;
  }

  public Runnable enableSearch(final String option) {
    return null;
  }

  public JComponent createComponent() {
    return myRootConfigurable != null ? myRootConfigurable.createComponent() : null;
  }

  public boolean isModified() {
    return myRootConfigurable != null && myRootConfigurable.isModified();
  }

  public void reset() {
    if (myRootConfigurable != null) {
      myRootConfigurable.reset();
    }
  }

  public void disposeUIResources() {
    if (myRootConfigurable != null) {
      myRootConfigurable.disposeUIResources();
    }
  }

  @NotNull
  @NonNls
  public String getId() {
    return "project.propDebugger";
  }
}