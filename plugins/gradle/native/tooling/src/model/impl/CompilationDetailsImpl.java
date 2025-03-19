// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.nativeplatform.tooling.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.DefaultExternalTask;
import org.jetbrains.plugins.gradle.model.ExternalTask;
import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.CompilationDetails;
import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.MacroDirective;
import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.SourceFile;

import java.io.File;
import java.util.*;

/**
 * @author Vladislav.Soroka
 */
public class CompilationDetailsImpl implements CompilationDetails {
  private File compilerExecutable;

  private ExternalTask compileTask;

  private File compileWorkingDir;
  private @NotNull List<File> frameworkSearchPaths;
  private @NotNull List<File> systemHeaderSearchPaths;
  private @NotNull List<File> userHeaderSearchPaths;
  private @NotNull Set<SourceFile> sources;
  private @NotNull Set<File> headerDirs;
  private @NotNull Set<MacroDirective> macroDefines;
  private @NotNull Set<String> macroUndefines;
  private @NotNull List<String> additionalArgs;

  public CompilationDetailsImpl() {
    frameworkSearchPaths = Collections.emptyList();
    systemHeaderSearchPaths = Collections.emptyList();
    userHeaderSearchPaths = Collections.emptyList();
    sources = Collections.emptySet();
    headerDirs = Collections.emptySet();
    macroDefines = Collections.emptySet();
    macroUndefines = Collections.emptySet();
    additionalArgs = Collections.emptyList();
  }

  public CompilationDetailsImpl(CompilationDetails compilationDetails) {
    compilerExecutable = compilationDetails.getCompilerExecutable();
    compileTask = new DefaultExternalTask(compilationDetails.getCompileTask());
    compileWorkingDir = compilationDetails.getCompileWorkingDir();
    frameworkSearchPaths = new ArrayList<>(compilationDetails.getFrameworkSearchPaths());
    systemHeaderSearchPaths = new ArrayList<>(compilationDetails.getSystemHeaderSearchPaths());
    userHeaderSearchPaths = new ArrayList<>(compilationDetails.getUserHeaderSearchPaths());
    sources = new LinkedHashSet<>(compilationDetails.getSources().size());
    for (SourceFile source : compilationDetails.getSources()) {
      sources.add(new SourceFileImpl(source));
    }
    headerDirs = new LinkedHashSet<>(compilationDetails.getHeaderDirs());

    macroDefines = new LinkedHashSet<>(compilationDetails.getMacroDefines().size());
    for (MacroDirective macroDirective : compilationDetails.getMacroDefines()) {
      macroDefines.add(new MacroDirectiveImpl(macroDirective));
    }
    macroUndefines = new LinkedHashSet<>(compilationDetails.getMacroUndefines());
    additionalArgs = new ArrayList<>(compilationDetails.getAdditionalArgs());
  }

  @Override
  public ExternalTask getCompileTask() {
    return compileTask;
  }

  public void setCompileTask(ExternalTask compileTask) {
    this.compileTask = compileTask;
  }

  @Override
  public @Nullable File getCompilerExecutable() {
    return compilerExecutable;
  }

  public void setCompilerExecutable(File compilerExecutable) {
    this.compilerExecutable = compilerExecutable;
  }

  @Override
  public File getCompileWorkingDir() {
    return compileWorkingDir;
  }

  public void setCompileWorkingDir(File compileWorkingDir) {
    this.compileWorkingDir = compileWorkingDir;
  }

  @Override
  public @NotNull List<File> getFrameworkSearchPaths() {
    return frameworkSearchPaths;
  }

  public void setFrameworkSearchPaths(@NotNull List<File> frameworkSearchPaths) {
    this.frameworkSearchPaths = frameworkSearchPaths;
  }

  @Override
  public @NotNull List<File> getSystemHeaderSearchPaths() {
    return systemHeaderSearchPaths;
  }

  public void setSystemHeaderSearchPaths(@NotNull List<File> systemHeaderSearchPaths) {
    this.systemHeaderSearchPaths = systemHeaderSearchPaths;
  }

  @Override
  public @NotNull List<File> getUserHeaderSearchPaths() {
    return userHeaderSearchPaths;
  }

  public void setUserHeaderSearchPaths(@NotNull List<File> userHeaderSearchPaths) {
    this.userHeaderSearchPaths = userHeaderSearchPaths;
  }

  @Override
  public @NotNull Set<? extends SourceFile> getSources() {
    return sources;
  }

  public void setSources(@NotNull Set<SourceFile> sources) {
    this.sources = sources;
  }

  @Override
  public @NotNull Set<File> getHeaderDirs() {
    return headerDirs;
  }

  public void setHeaderDirs(@NotNull Set<File> headerDirs) {
    this.headerDirs = headerDirs;
  }


  @Override
  public @NotNull Set<? extends MacroDirective> getMacroDefines() {
    return macroDefines;
  }

  public void setMacroDefines(@NotNull Set<MacroDirective> macroDefines) {
    this.macroDefines = macroDefines;
  }

  @Override
  public @NotNull Set<String> getMacroUndefines() {
    return macroUndefines;
  }

  public void setMacroUndefines(@NotNull Set<String> macroUndefines) {
    this.macroUndefines = macroUndefines;
  }


  @Override
  public @NotNull List<String> getAdditionalArgs() {
    return additionalArgs;
  }

  public void setAdditionalArgs(@NotNull List<String> additionalArgs) {
    this.additionalArgs = additionalArgs;
  }
}
