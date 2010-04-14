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

import com.intellij.openapi.project.Project;
import com.intellij.util.Icons;
import org.intellij.plugins.intelliLang.inject.config.MethodParameterInjection;
import org.intellij.plugins.intelliLang.inject.config.ui.MethodParameterPanel;

import javax.swing.*;

public class MethodParameterInjectionConfigurable extends InjectionConfigurable<MethodParameterInjection, MethodParameterPanel> {
  public MethodParameterInjectionConfigurable(MethodParameterInjection injection, Runnable treeUpdater, Project project) {
    super(injection, treeUpdater, project);
  }

  protected MethodParameterPanel createOptionsPanelImpl() {
    return new MethodParameterPanel(myInjection, myProject);
  }

  public String getBannerSlogan() {
    return "Edit Method Parameter Injection";
  }

  public Icon getIcon() {
    return Icons.METHOD_ICON;
  }
}
