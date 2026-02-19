// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyGraphModel;

import com.intellij.openapi.externalSystem.model.project.dependencies.DependencyScopeNode;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GradleDependencyReportTask extends DefaultTask {
  private Path outputPath;

  final Provider<String> reportContent = getProject().getProviders().provider(() -> collectDependencies(getProject()));

  @OutputFile
  public Path getOutputPath() {
    return outputPath;
  }

  public void setOutputPath(Path outputPath) {
    this.outputPath = outputPath;
  }

  @TaskAction
  public void generate() throws IOException {
    byte[] content = reportContent.get().getBytes(StandardCharsets.UTF_8);
    Files.write(outputPath, content);
  }

  public String collectDependencies(Project project) {
    GradleDependencyReportGenerator generator = new GradleDependencyReportGenerator();
    List<DependencyScopeNode> dependencyScopes = new ArrayList<>();
    project.getConfigurations().all(configuration -> {
      if (configuration.isCanBeResolved()) {
        try {
          DependencyScopeNode dependencyScope = generator.buildDependencyGraph(configuration, project);
          dependencyScopes.add(dependencyScope);
        }
        catch (Exception ignored) {
          // Some plugins (e.g., Quarkus) may expose special configurations that cannot be safely resolved
          // in this context. Skip such configurations to keep the report generation resilient.
        }
      }
    });
    return GradleDependencyNodeDeserializer.toJson(dependencyScopes);
  }
}
