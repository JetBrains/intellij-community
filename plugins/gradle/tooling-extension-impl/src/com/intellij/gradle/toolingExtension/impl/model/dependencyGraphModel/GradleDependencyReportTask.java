// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyGraphModel;

import com.google.gson.GsonBuilder;
import com.intellij.openapi.externalSystem.model.project.dependencies.DependencyScopeNode;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GradleDependencyReportTask extends DefaultTask {

  private List<String> configurations;
  private File outputFile;

  @Input
  public List<String> getConfigurations() {
    return configurations;
  }

  public void setConfigurations(List<String> configurations) {
    this.configurations = configurations;
  }

  @OutputFile
  public File getOutputFile() {
    return outputFile;
  }

  public void setOutputFile(File outputFile) {
    this.outputFile = outputFile;
  }

  @TaskAction
  public void generate() throws IOException {
    Collection<Configuration> configurations = getSelectedConfigurations();
    GradleDependencyReportGenerator generator = new GradleDependencyReportGenerator();
    List<DependencyScopeNode> graph = new ArrayList<>();
    for (Configuration configuration : configurations) {
      if (configuration.isCanBeResolved()) {
        graph.add(generator.buildDependencyGraph(configuration, getProject()));
      }
    }

    Path outputFilePath = outputFile.toPath();
    Files.createDirectories(outputFilePath.getParent());
    Files.write(outputFilePath, new GsonBuilder().create().toJson(graph).getBytes(StandardCharsets.UTF_8));
  }

  @NotNull
  private Collection<Configuration> getSelectedConfigurations() {
    if (configurations.isEmpty()) {
      return getProject().getConfigurations();
    }
    Collection<Configuration> selectedProjectConfigurations = new ArrayList<>();
    for (String configurationName : configurations) {
      Configuration configuration = getProject().getConfigurations().findByName(configurationName);
      if (configuration != null) {
        selectedProjectConfigurations.add(configuration);
      }
    }
    return selectedProjectConfigurations;
  }
}
