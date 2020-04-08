// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport;

import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.openapi.externalSystem.model.project.ProjectId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Vladislav.Soroka
 */
public class GradleGroovyFrameworkSupportProvider extends GradleFrameworkSupportProvider {

  public static final String ID = "groovy";

  @NotNull
  @Override
  public FrameworkTypeEx getFrameworkType() {
    return new FrameworkTypeEx(ID) {
      @NotNull
      @Override
      public FrameworkSupportInModuleProvider createProvider() {
        return GradleGroovyFrameworkSupportProvider.this;
      }

      @NotNull
      @Override
      public String getPresentableName() {
        //noinspection HardCodedStringLiteral
        return "Groovy";//NON-NLS
      }

      @NotNull
      @Override
      public Icon getIcon() {
        return JetgroovyIcons.Groovy.Groovy_16x16;
      }
    };
  }

  @Override
  public void addSupport(@NotNull ProjectId projectId,
                         @NotNull Module module,
                         @NotNull ModifiableRootModel rootModel,
                         @NotNull ModifiableModelsProvider modifiableModelsProvider,
                         @NotNull BuildScriptDataBuilder buildScriptData) {
    buildScriptData
      .addPluginDefinitionInPluginsGroup("id 'groovy'")
      .addRepositoriesDefinition("mavenCentral()")
      .addDependencyNotation("compile 'org.codehaus.groovy:groovy-all:2.3.11'")
      .addDependencyNotation("testCompile group: 'junit', name: 'junit', version: '4.12'");
  }
}