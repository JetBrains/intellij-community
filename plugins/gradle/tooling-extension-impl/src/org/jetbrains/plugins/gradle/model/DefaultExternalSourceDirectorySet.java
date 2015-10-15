/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.model;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 7/14/2014
 */
public class DefaultExternalSourceDirectorySet implements ExternalSourceDirectorySet {
  private static final long serialVersionUID = 1L;

  @NotNull
  private String myName;
  @NotNull
  private Set<File> mySrcDirs;
  @NotNull
  private File myOutputDir;
  @NotNull
  private Set<String> myExcludes;
  @NotNull
  private Set<String> myIncludes;
  @NotNull
  private List<ExternalFilter> myFilters;

  private boolean myInheritedCompilerOutput;

  public DefaultExternalSourceDirectorySet() {
    mySrcDirs = new HashSet<File>();
    myExcludes = new HashSet<String>();
    myIncludes = new HashSet<String>();
    myFilters = new ArrayList<ExternalFilter>();
  }

  public DefaultExternalSourceDirectorySet(ExternalSourceDirectorySet sourceDirectorySet) {
    this();
    myName = sourceDirectorySet.getName();
    mySrcDirs = new HashSet<File>(sourceDirectorySet.getSrcDirs());
    myOutputDir = sourceDirectorySet.getOutputDir();
    myExcludes = new HashSet<String>(sourceDirectorySet.getExcludes());
    myIncludes = new HashSet<String>(sourceDirectorySet.getIncludes());
    for (ExternalFilter filter : sourceDirectorySet.getFilters()) {
      myFilters.add(new DefaultExternalFilter(filter));
    }
    myInheritedCompilerOutput = sourceDirectorySet.isCompilerOutputPathInherited();
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }

  @NotNull
  @Override
  public Set<File> getSrcDirs() {
    return mySrcDirs;
  }

  public void setSrcDirs(@NotNull Set<File> srcDirs) {
    mySrcDirs = srcDirs;
  }

  @NotNull
  @Override
  public File getOutputDir() {
    return myOutputDir;
  }

  @Override
  public boolean isCompilerOutputPathInherited() {
    return myInheritedCompilerOutput;
  }

  public void setInheritedCompilerOutput(boolean inheritedCompilerOutput) {
    myInheritedCompilerOutput = inheritedCompilerOutput;
  }

  @NotNull
  @Override
  public Set<String> getIncludes() {
    return myIncludes;
  }

  public void setIncludes(@NotNull Set<String> includes) {
    myIncludes = includes;
  }

  @NotNull
  @Override
  public Set<String> getExcludes() {
    return myExcludes;
  }

  public void setExcludes(@NotNull Set<String> excludes) {
    myExcludes = excludes;
  }

  @NotNull
  @Override
  public List<ExternalFilter> getFilters() {
    return myFilters;
  }

  public void setFilters(@NotNull List<ExternalFilter> filters) {
    myFilters = filters;
  }

  public void setOutputDir(@NotNull File outputDir) {
    myOutputDir = outputDir;
  }
}
