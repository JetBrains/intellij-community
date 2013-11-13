/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 11/6/13
 */
public class WarModelData extends AbstractExternalEntityData {
  private static final long serialVersionUID = 1L;

  @NotNull
  public static final Key<WarModelData> KEY = Key.create(WarModelData.class, ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight() + 1);
  @NotNull
  private final String myWebAppDirName;
  @NotNull
  private final File myWebAppDir;
  @Nullable
  private File myWebXml;
  @NotNull
  private Map<String, Set<String>> myWebRoots;
  @NotNull
  private Set<File> myClasspath;
  @Nullable
  private String myManifestContent;


  public WarModelData(@NotNull ProjectSystemId owner, @NotNull String webAppDirName, @NotNull File webAppDir) {
    super(owner);
    myWebAppDirName = webAppDirName;
    myWebAppDir = webAppDir;
    myWebRoots = Collections.emptyMap();
    myClasspath = Collections.emptySet();
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

  public void setWebRoots(@Nullable Map<String, Set<String>> webRoots) {
    myWebRoots = webRoots == null ? Collections.<String, Set<String>>emptyMap() : webRoots;
  }

  @NotNull
  public Map<String, Set<String>> getWebRoots() {
    return myWebRoots;
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

    WarModelData that = (WarModelData)o;

    if (!myWebAppDirName.equals(that.myWebAppDirName)) return false;
    if (!myWebRoots.equals(that.myWebRoots)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myWebAppDirName.hashCode();
    result = 31 * result + myWebRoots.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "WarModelData{" +
           "myWebAppDirName='" + myWebAppDirName + '\'' +
           ", myWebAppDir=" + myWebAppDir +
           ", myWebXml=" + myWebXml +
           ", myWebRoots=" + myWebRoots +
           '}';
  }
}
