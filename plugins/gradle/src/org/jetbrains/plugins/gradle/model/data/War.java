/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.model.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 2/10/14
 */
public class War implements Serializable {
  private static final long serialVersionUID = 1L;

  @NotNull
  private final String myName;

  @NotNull
  private final String myWebAppDirName;
  @NotNull
  private final File myWebAppDir;
  @Nullable
  private File myWebXml;
  @NotNull
  private List<WebResource> myWebResources;
  @NotNull
  private Set<File> myClasspath;
  @Nullable
  private String myManifestContent;


  public War(@NotNull String name, @NotNull String webAppDirName, @NotNull File webAppDir) {
    myName = name;
    myWebAppDirName = webAppDirName;
    myWebAppDir = webAppDir;
    myWebResources = Collections.emptyList();
    myClasspath = Collections.emptySet();
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getWebAppDirName() {
    return myWebAppDirName;
  }

  @NotNull
  public File getWebAppDir() {
    return myWebAppDir;
  }

  public void setWebXml(@Nullable File webXml) {
    myWebXml = webXml;
  }

  @Nullable
  public File getWebXml() {
    return myWebXml;
  }

  public void setWebResources(@Nullable List<WebResource> webResources) {
    myWebResources = webResources == null ? Collections.<WebResource>emptyList() : webResources;
  }

  @NotNull
  public List<WebResource> getWebResources() {
    return myWebResources;
  }

  public void setClasspath(@Nullable Set<File> classpath) {
    myClasspath = classpath == null ? Collections.<File>emptySet() : classpath;
  }

  @NotNull
  public Set<File> getClasspath() {
    return myClasspath;
  }

  public void setManifestContent(@Nullable String manifestContent) {
    myManifestContent = manifestContent;
  }

  @Nullable
  public String getManifestContent() {
    return myManifestContent;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    War that = (War)o;

    if (!myName.equals(that.myName)) return false;
    if (!myWebAppDirName.equals(that.myWebAppDirName)) return false;
    if (!myWebResources.equals(that.myWebResources)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myName.hashCode();
    result = 31 * result + myWebAppDirName.hashCode();
    result = 31 * result + myWebResources.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "War{" +
           "myName='" + myName + '\'' +
           ", myWebAppDirName='" + myWebAppDirName + '\'' +
           ", myWebAppDir=" + myWebAppDir +
           ", myWebXml=" + myWebXml +
           ", myWebResources=" + myWebResources +
           '}';
  }
}
