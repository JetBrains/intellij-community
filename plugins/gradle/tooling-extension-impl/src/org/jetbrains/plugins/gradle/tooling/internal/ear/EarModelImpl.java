// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.internal.ear;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ear.EarConfiguration;

import java.io.File;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public class EarModelImpl implements EarConfiguration.EarModel {
  private final @NotNull String myEarName;
  private final String myAppDirName;
  private final String myLibDirName;
  private File myArchivePath;
  private List<EarConfiguration.EarResource> myEarResources;
  private String myManifestContent;
  private String myDeploymentDescriptor;

  public EarModelImpl(@NotNull String name, @NotNull String appDirName, String libDirName) {
    myEarName = name;
    myAppDirName = appDirName;
    myLibDirName = libDirName;
  }

  @Override
  public @NotNull String getEarName() {
    return myEarName;
  }

  @Override
  public File getArchivePath() {
    return myArchivePath;
  }

  public void setArchivePath(File artifactFile) {
    myArchivePath = artifactFile;
  }

  @Override
  public @NotNull String getAppDirName() {
    return myAppDirName;
  }

  @Override
  public String getLibDirName() {
    return myLibDirName;
  }

  @Override
  public List<EarConfiguration.EarResource> getResources() {
    return myEarResources;
  }

  public void setResources(List<EarConfiguration.EarResource> earResources) {
    myEarResources = earResources;
  }

  public void setManifestContent(String manifestContent) {
    myManifestContent = manifestContent;
  }

  @Override
  public String getManifestContent() {
    return myManifestContent;
  }

  @Override
  public String getDeploymentDescriptor() {
    return myDeploymentDescriptor;
  }

  public void setDeploymentDescriptor(String deploymentDescriptor) {
    myDeploymentDescriptor = deploymentDescriptor;
  }
}
