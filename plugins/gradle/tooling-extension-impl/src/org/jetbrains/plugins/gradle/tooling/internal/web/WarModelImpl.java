// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.internal.web;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.web.WebConfiguration;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 */
public class WarModelImpl implements WebConfiguration.WarModel {
  private final @NotNull String warName;
  private final String myWebAppDirName;
  private final File myWebAppDir;
  private File myArchivePath;
  private File myWebXml;
  private List<WebConfiguration.WebResource> myWebResources;
  private Set<File> myClasspath;
  private String myManifestContent;

  public WarModelImpl(@NotNull String name, String webAppDirName, File webAppDir) {
    warName = name;
    myWebAppDirName = webAppDirName;
    myWebAppDir = webAppDir;
  }

  @Override
  public @NotNull String getWarName() {
    return warName;
  }

  @Override
  public File getArchivePath() {
    return myArchivePath;
  }

  public void setArchivePath(File artifactFile) {
    myArchivePath = artifactFile;
  }

  @Override
  public String getWebAppDirName() {
    return myWebAppDirName;
  }

  @Override
  public File getWebAppDir() {
    return myWebAppDir;
  }

  public void setWebXml(File webXml) {
    myWebXml = webXml;
  }

  @Override
  public File getWebXml() {
    return myWebXml;
  }

  @Override
  public List<WebConfiguration.WebResource> getWebResources() {
    return myWebResources;
  }

  public void setWebResources(List<WebConfiguration.WebResource> webResources) {
    myWebResources = webResources;
  }

  public void setClasspath(Set<File> classpath) {
    myClasspath = classpath;
  }

  @Override
  public Set<File> getClasspath() {
    return myClasspath;
  }

  public void setManifestContent(String manifestContent) {
    myManifestContent = manifestContent;
  }

  @Override
  public String getManifestContent() {
    return myManifestContent;
  }
}
