// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

/**
 * @author Vladislav.Soroka
 */
public final class DefaultExternalSourceDirectorySet implements ExternalSourceDirectorySet {
  private static final long serialVersionUID = 1L;

  private String name;
  private @NotNull Set<File> srcDirs;
  private File outputDir;
  private @NotNull Collection<File> gradleOutputDirs;
  private final FilePatternSetImpl patterns;
  private @NotNull List<DefaultExternalFilter> filters;

  private boolean isCompilerOutputInherited;

  public DefaultExternalSourceDirectorySet() {
    srcDirs = new HashSet<>(0);
    filters = new ArrayList<>(0);
    gradleOutputDirs = new ArrayList<>(0);
    patterns = new FilePatternSetImpl();
  }

  @Override
  public @NotNull String getName() {
    return name;
  }

  public void setName(@NotNull String name) {
    this.name = name;
  }

  @Override
  public @NotNull Set<File> getSrcDirs() {
    return srcDirs;
  }

  public void setSrcDirs(@NotNull Set<File> srcDirs) {
    this.srcDirs = srcDirs;
  }

  @Override
  public @NotNull File getOutputDir() {
    return outputDir;
  }

  public void setOutputDir(@NotNull File outputDir) {
    this.outputDir = outputDir;
  }

  @Override
  public @NotNull Collection<File> getGradleOutputDirs() {
    return gradleOutputDirs;
  }

  public void setGradleOutputDirs(@NotNull Collection<File> gradleOutputDirs) {
    this.gradleOutputDirs = gradleOutputDirs;
  }

  @Override
  public boolean isCompilerOutputPathInherited() {
    return isCompilerOutputInherited;
  }

  public void setCompilerOutputPathInherited(boolean isCompilerOutputInherited) {
    this.isCompilerOutputInherited = isCompilerOutputInherited;
  }

  @Override
  public @NotNull Set<String> getExcludes() {
    return patterns.getExcludes();
  }

  public void setExcludes(Set<String> excludes) {
    patterns.setExcludes(excludes);
  }

  @Override
  public @NotNull Set<String> getIncludes() {
    return patterns.getIncludes();
  }

  public void setIncludes(Set<String> includes) {
    patterns.setIncludes(includes);
  }

  @Override
  public @NotNull FilePatternSet getPatterns() {
    return patterns;
  }

  public void setPatterns(@NotNull FilePatternSet patterns) {
    this.patterns.setIncludes(patterns.getIncludes());
    this.patterns.setExcludes(patterns.getExcludes());
  }

  @Override
  public @NotNull List<DefaultExternalFilter> getFilters() {
    return filters;
  }

  public void setFilters(@NotNull List<DefaultExternalFilter> filters) {
    this.filters = filters;
  }
}
