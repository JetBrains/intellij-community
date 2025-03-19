// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.ant.model.impl;

import com.intellij.lang.ant.config.impl.BuildFileProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ant.model.JpsAntBuildFileOptions;

import java.util.ArrayList;
import java.util.List;

public final class JpsAntBuildFileOptionsImpl implements JpsAntBuildFileOptions {
  private int myMaxStackSize = 2;
  private String myAntCommandLineParameters = "";
  private int myMaxHeapSize = 128;
  private String myCustomJdkName = "";
  private boolean myUseProjectDefaultAnt = true;
  private String myAntInstallationName;
  private final List<String> myClasspath = new ArrayList<>();
  private final List<String> myJarDirectories = new ArrayList<>();
  private final List<BuildFileProperty> myProperties = new ArrayList<>();

  public void setMaxStackSize(int maxStackSize) {
    myMaxStackSize = maxStackSize;
  }

  @Override
  public void setAntCommandLineParameters(String antCommandLineParameters) {
    myAntCommandLineParameters = antCommandLineParameters;
  }

  @Override
  public void setUseProjectDefaultAnt(boolean useProjectDefaultAnt) {
    myUseProjectDefaultAnt = useProjectDefaultAnt;
  }

  @Override
  public String getAntInstallationName() {
    return myAntInstallationName;
  }

  public void setAntInstallationName(String antInstallationName) {
    myAntInstallationName = antInstallationName;
  }

  @Override
  public void addJarPath(@NotNull String path) {
    myClasspath.add(path);
  }

  @Override
  public void addJarDirectory(@NotNull String directoryPath) {
    myJarDirectories.add(directoryPath);
  }

  public void setMaxHeapSize(int maxHeapSize) {
    myMaxHeapSize = maxHeapSize;
  }

  public void setCustomJdkName(String customJdkName) {
    myCustomJdkName = customJdkName;
  }

  @Override
  public int getMaxHeapSize() {
    return myMaxHeapSize;
  }

  @Override
  public int getMaxStackSize() {
    return myMaxStackSize;
  }

  @Override
  public String getCustomJdkName() {
    return myCustomJdkName;
  }

  @Override
  public String getAntCommandLineParameters() {
    return myAntCommandLineParameters;
  }

  @Override
  public boolean isUseProjectDefaultAnt() {
    return myUseProjectDefaultAnt;
  }

  @Override
  public List<String> getAdditionalClasspath() {
    return JpsAntInstallationImpl.getClasspath(myClasspath, myJarDirectories);
  }

  @Override
  public void addProperty(@NotNull String name, @NotNull String value) {
    myProperties.add(new BuildFileProperty(name, value));
  }

  @Override
  public @NotNull List<BuildFileProperty> getProperties() {
    return myProperties;
  }
}
