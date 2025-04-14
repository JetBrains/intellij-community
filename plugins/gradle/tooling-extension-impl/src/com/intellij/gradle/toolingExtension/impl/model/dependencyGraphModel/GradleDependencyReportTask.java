// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyGraphModel;

import com.intellij.openapi.externalSystem.model.project.dependencies.DependencyScopeNode;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GradleDependencyReportTask extends DefaultTask {

  private byte[] content;
  private Path outputPath;

  @Input
  public byte[] getContent() {
    return content;
  }

  public void setContent(byte[] content) {
    this.content = content;
  }

  @OutputFile
  public Path getOutputPath() {
    return outputPath;
  }

  public void setOutputPath(Path outputPath) {
    this.outputPath = outputPath;
  }

  @TaskAction
  public void generate() throws IOException {
    Files.write(outputPath, content);
  }

  public static byte[] collectDependencies(Project project) {
    GradleDependencyReportGenerator generator = new GradleDependencyReportGenerator();
    List<DependencyScopeNode> dependencyScopes = new ArrayList<>();
    project.getConfigurations().all(configuration -> {
      if (configuration.isCanBeResolved()) {
        DependencyScopeNode dependencyScope = generator.buildDependencyGraph(configuration, project);
        dependencyScopes.add(dependencyScope);
      }
    });
    return GradleDependencyNodeDeserializer.toJson(dependencyScopes);
  }
}
