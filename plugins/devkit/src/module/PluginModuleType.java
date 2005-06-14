/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package org.jetbrains.idea.devkit.module;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectWizardStepFactory;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class PluginModuleType extends ModuleType<PluginModuleBuilder> {
  private static final Icon PLUGIN_MODULE_ICON = IconLoader.getIcon("/general/pluginManager.png");
  private static final Icon PLUGIN_MODULE_NODE_ICON = IconLoader.getIcon("/nodes/plugin.png");
  private static final Icon ADD_PLUGIN_MODULE_ICON = IconLoader.getIcon("/add_plugin_modulewizard.png");
  private static PluginModuleType ourInstance = new PluginModuleType();

  private PluginModuleType() {
    super("PLUGIN_MODULE");
  }

  public static PluginModuleType getInstance() {
    return ourInstance;
  }

  public final boolean isJ2EE() {
    return false;
  }

  public ModuleWizardStep[] createWizardSteps(WizardContext wizardContext, PluginModuleBuilder pluginModuleBuilder, ModulesProvider modulesProvider) {
    final ProjectWizardStepFactory stepFactory = ProjectWizardStepFactory.getInstance();
    final ModuleWizardStep nameAndLocationStep = stepFactory.createNameAndLocationStep(wizardContext, pluginModuleBuilder, modulesProvider, ADD_PLUGIN_MODULE_ICON, null);
    return new ModuleWizardStep[] {
      nameAndLocationStep,
      stepFactory.createSourcePathsStep(nameAndLocationStep, pluginModuleBuilder, ADD_PLUGIN_MODULE_ICON, null),
      stepFactory.createOutputPathPathsStep(nameAndLocationStep, pluginModuleBuilder, ADD_PLUGIN_MODULE_ICON, null)
    };
  }

  public PluginModuleBuilder createModuleBuilder() {
    return new PluginModuleBuilder();
  }

  public String getName() {
    return "Plugin Module";
  }

  public String getDescription() {
    return "<html>This module type is designed to ease <b>development of Plugins</b> for IntelliJ IDEA. It allows you to properly configure" +
           "target IntelliJ IDEA SDK and all necessary deployment settings. You can also configure running/debugging instance of IntelliJ IDEA" +
           "with your plugin enabled.</html>";
  }

  public Icon getBigIcon() {
    return PLUGIN_MODULE_ICON;
  }

  public Icon getNodeIcon(boolean isOpened) {
    return PLUGIN_MODULE_NODE_ICON;
  }
}