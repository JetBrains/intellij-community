// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.data;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 */
public class War extends Jar {
  private static final long serialVersionUID = 1L;

  private final @NotNull String webAppDirName;
  private final @NotNull File webAppDir;
  private @Nullable File webXml;
  private @NotNull List<WebResource> webResources;
  private @NotNull Set<File> classpath;

  @PropertyMapping({"name", "webAppDirName", "webAppDir"})
  public War(@NotNull String name, @NotNull String webAppDirName, @NotNull File webAppDir) {
    super(name);
    this.webAppDirName = webAppDirName;
    this.webAppDir = webAppDir;
    webResources = Collections.emptyList();
    classpath = Collections.emptySet();
  }

  public @NotNull String getWebAppDirName() {
    return webAppDirName;
  }

  public @NotNull File getWebAppDir() {
    return webAppDir;
  }

  public void setWebXml(@Nullable File webXml) {
    this.webXml = webXml;
  }

  public @Nullable File getWebXml() {
    return webXml;
  }

  public void setWebResources(@Nullable List<WebResource> webResources) {
    this.webResources = webResources == null ? Collections.emptyList() : webResources;
  }

  public @NotNull List<WebResource> getWebResources() {
    return webResources;
  }

  public void setClasspath(@Nullable Set<File> classpath) {
    this.classpath = classpath == null ? Collections.emptySet() : classpath;
  }

  public @NotNull Set<File> getClasspath() {
    return classpath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof War war)) return false;
    if (!super.equals(o)) return false;

    if (!webAppDirName.equals(war.webAppDirName)) return false;
    if (!webResources.equals(war.webResources)) return false;
    if (!classpath.equals(war.classpath)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + webAppDirName.hashCode();
    result = 31 * result + webResources.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "War{" +
           "name='" + getName() + '\'' +
           ", webAppDirName='" + webAppDirName + '\'' +
           ", webAppDir=" + webAppDir +
           ", webXml=" + webXml +
           ", webResources=" + webResources +
           '}';
  }
}
