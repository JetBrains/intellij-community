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

public class KotlinDslGradleGroovyFrameworkSupportProvider extends KotlinDslGradleFrameworkSupportProvider {

  public static final String ID = "groovy";

  @NotNull
  @Override
  public FrameworkTypeEx getFrameworkType() {
    return new FrameworkTypeEx(ID) {
      @NotNull
      @Override
      public FrameworkSupportInModuleProvider createProvider() {
        return KotlinDslGradleGroovyFrameworkSupportProvider.this;
      }

      @NotNull
      @Override
      public String getPresentableName() {
        //noinspection HardCodedStringLiteral
        return "Groovy"; //NON-NLS
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
      .addPluginDefinitionInPluginsGroup("groovy")
      .addRepositoriesDefinition("mavenCentral()")
      .addOther("""
                  tasks.getByName<Test>("test") {
                      useJUnitPlatform()
                  }""")
      .addDependencyNotation("implementation(\"org.codehaus.groovy:groovy-all:3.0.5\")")
      .addDependencyNotation("testImplementation(\"org.junit.jupiter:junit-jupiter-api:5.6.0\")")
      .addDependencyNotation("testRuntimeOnly(\"org.junit.jupiter:junit-jupiter-engine\")");
  }
}