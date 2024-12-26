// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public final class KotlinDslGradleJavaFrameworkSupportProvider extends KotlinDslGradleFrameworkSupportProvider {

  public static final String ID = "java";

  @Override
  public @NotNull FrameworkTypeEx getFrameworkType() {
    return new FrameworkTypeEx(ID) {
      @Override
      public @NotNull FrameworkSupportInModuleProvider createProvider() {
        return KotlinDslGradleJavaFrameworkSupportProvider.this;
      }

      @Override
      public @NotNull String getPresentableName() {
        //noinspection HardCodedStringLiteral
        return "Java"; //NON-NLS
      }

      @Override
      public @NotNull Icon getIcon() {
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
      .withMavenCentral()
      .addOther("""
                  tasks.getByName<Test>("test") {
                      useJUnitPlatform()
                  }""")
      .addDependencyNotation("testImplementation(\"org.junit.jupiter:junit-jupiter-api:5.6.0\")")
      .addDependencyNotation("testRuntimeOnly(\"org.junit.jupiter:junit-jupiter-engine\")");
  }
}
