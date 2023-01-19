// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport;

import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.externalSystem.model.project.ProjectId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class KotlinDslGradleJavaFrameworkSupportProvider extends KotlinDslGradleFrameworkSupportProvider {

  public static final String ID = "java";

  @NotNull
  @Override
  public FrameworkTypeEx getFrameworkType() {
    return new FrameworkTypeEx(ID) {
      @NotNull
      @Override
      public FrameworkSupportInModuleProvider createProvider() {
        return KotlinDslGradleJavaFrameworkSupportProvider.this;
      }

      @NotNull
      @Override
      public String getPresentableName() {
        //noinspection HardCodedStringLiteral
        return "Java"; //NON-NLS
      }

      @NotNull
      @Override
      public Icon getIcon() {
        return AllIcons.Nodes.Module;
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
      .addPluginDefinitionInPluginsGroup("java")
      .addRepositoriesDefinition("mavenCentral()")
      .addOther("""
                  tasks.getByName<Test>("test") {
                      useJUnitPlatform()
                  }""")
      .addDependencyNotation("testImplementation(\"org.junit.jupiter:junit-jupiter-api:5.6.0\")")
      .addDependencyNotation("testRuntimeOnly(\"org.junit.jupiter:junit-jupiter-engine\")");
  }
}
