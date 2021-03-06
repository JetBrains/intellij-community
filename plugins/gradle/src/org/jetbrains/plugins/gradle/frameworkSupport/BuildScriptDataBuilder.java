// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * @author Vladislav.Soroka
 */
public class BuildScriptDataBuilder {
  private final static Logger LOG = Logger.getInstance(BuildScriptDataBuilder.class);

  @NotNull private final VirtualFile myBuildScriptFile;
  protected final Set<String> imports = new TreeSet<>();
  protected final Set<String> plugins = new TreeSet<>();
  protected final Set<String> pluginsInGroup = new TreeSet<>();
  protected final Set<String> repositories = new TreeSet<>();
  protected final Set<String> dependencies = new TreeSet<>();
  protected final Set<String> properties = new TreeSet<>();
  protected final Set<String> buildScriptProperties = new TreeSet<>();
  protected final Set<String> buildScriptRepositories = new TreeSet<>();
  protected final Set<String> buildScriptDependencies = new TreeSet<>();
  protected final Set<String> other = new TreeSet<>();
  protected final GradleVersion myGradleVersion;

  public BuildScriptDataBuilder(@NotNull VirtualFile buildScriptFile) {
    this(buildScriptFile, GradleVersion.current());
  }

  public BuildScriptDataBuilder(@NotNull VirtualFile buildScriptFile, @NotNull GradleVersion gradleVersion) {
    myBuildScriptFile = buildScriptFile;
    myGradleVersion = gradleVersion;
  }

  @NotNull
  public VirtualFile getBuildScriptFile() {
    return myBuildScriptFile;
  }

  @NotNull
  public GradleVersion getGradleVersion() {
    return myGradleVersion;
  }

  public String buildImports() {
    if (!imports.isEmpty()) {
      return StringUtil.join(imports, "\n") + "\n";
    }

    return "";
  }

  public String buildConfigurationPart() {
    List<String> lines = new ArrayList<>();
    addBuildscriptLines(lines, BuildScriptDataBuilder::padding);
    if (!pluginsInGroup.isEmpty()) {
      lines.add("plugins {");
      lines.addAll(ContainerUtil.map(pluginsInGroup, BuildScriptDataBuilder::padding));
      lines.add("}");
      lines.add("");
    }
    return StringUtil.join(lines, "\n");
  }

  public String buildMainPart() {
    List<String> lines = new ArrayList<>();
    addPluginsLines(lines, BuildScriptDataBuilder::padding);
    if (!properties.isEmpty()) {
      lines.addAll(properties);
      lines.add("");
    }
    if (!repositories.isEmpty()) {
      lines.add("repositories {");
      lines.addAll(ContainerUtil.map(repositories, BuildScriptDataBuilder::padding));
      lines.add("}");
      lines.add("");
    }
    if (!dependencies.isEmpty()) {
      lines.add("dependencies {");
      lines.addAll(ContainerUtil.map(dependencies, BuildScriptDataBuilder::padding));
      lines.add("}");
      lines.add("");
    }
    if (!other.isEmpty()) {
      lines.addAll(other);
    }
    return StringUtil.join(lines, "\n");
  }

  protected void addPluginsLines(@NotNull List<? super String> lines, @NotNull Function<? super String, String> padding) {
    if (!plugins.isEmpty()) {
      lines.addAll(plugins);
      lines.add("");
    }
  }

  private void addBuildscriptLines(@NotNull List<? super String> lines, @NotNull Function<? super String, String> padding) {
    if (!buildScriptRepositories.isEmpty() || !buildScriptDependencies.isEmpty() || !buildScriptProperties.isEmpty()) {
      lines.add("buildscript {");
      final List<String> buildScriptLines = new SmartList<>();
      if (!buildScriptProperties.isEmpty()) {
        buildScriptLines.addAll(buildScriptProperties);
        buildScriptLines.add("");
      }
      if (!buildScriptRepositories.isEmpty()) {
        buildScriptLines.add("repositories {");
        buildScriptLines.addAll(ContainerUtil.map(buildScriptRepositories, padding));
        buildScriptLines.add("}");
      }
      if (!buildScriptDependencies.isEmpty()) {
        buildScriptLines.add("dependencies {");
        buildScriptLines.addAll(ContainerUtil.map(buildScriptDependencies, padding));
        buildScriptLines.add("}");
      }
      lines.addAll(ContainerUtil.map(buildScriptLines, padding));
      lines.add("}");
      lines.add("");
    }
  }

  public BuildScriptDataBuilder addImport(@NonNls @NotNull String importString) {
    imports.add(importString);
    return this;
  }

  public BuildScriptDataBuilder addBuildscriptPropertyDefinition(@NonNls @NotNull String definition) {
    buildScriptProperties.add(definition.trim());
    return this;
  }

  public BuildScriptDataBuilder addBuildscriptRepositoriesDefinition(@NonNls @NotNull String definition) {
    buildScriptRepositories.add(definition.trim());
    return this;
  }

  public BuildScriptDataBuilder addBuildscriptDependencyNotation(@NonNls @NotNull String notation) {
    buildScriptDependencies.add(notation.trim());
    return this;
  }

  public BuildScriptDataBuilder addPluginDefinitionInPluginsGroup(@NonNls @NotNull String definition) {
    pluginsInGroup.add(definition.trim());
    return this;
  }

  public BuildScriptDataBuilder addPluginDefinition(@NonNls @NotNull String definition) {
    plugins.add(definition.trim());
    return this;
  }

  public BuildScriptDataBuilder addRepositoriesDefinition(@NonNls @NotNull String definition) {
    repositories.add(definition.trim());
    return this;
  }

  public BuildScriptDataBuilder addDependencyNotation(@NonNls @NotNull String notation) {
    if (notation.matches("\\s*(compile|testCompile|runtime|testRuntime)[^\\w].*")) {
      LOG.warn(notation);
      LOG.warn("compile, testCompile, runtime and testRuntime dependency notations were deprecated in Gradle 3.4, " +
               "use implementation, api, compileOnly and runtimeOnly instead", new Throwable());
    }
    dependencies.add(notation.trim());
    return this;
  }

  public BuildScriptDataBuilder addPropertyDefinition(@NonNls @NotNull String definition) {
    properties.add(definition.trim());
    return this;
  }

  public BuildScriptDataBuilder addOther(@NonNls @NotNull String definition) {
    other.add(definition.trim());
    return this;
  }

  private static String padding(@NonNls String s) {return StringUtil.isNotEmpty(s) ? "    " + s : "";}
}
