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
package org.jetbrains.plugins.gradle.model.internal;

import org.jetbrains.plugins.gradle.model.WarModel;

import java.io.File;
import java.util.Map;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 11/5/13
 */
public class WarModelImpl implements WarModel {
  private final String myWebAppDirName;
  private final File myWebAppDir;
  private File myWebXml;
  private Map<String, Set<String>> myWebRoots;
  private Set<File> myClasspath;
  private String myManifestContent;

  public WarModelImpl(String webAppDirName, File webAppDir) {
    myWebAppDirName = webAppDirName;
    myWebAppDir = webAppDir;
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
  public Map<String, Set<String>> getWebRoots() {
    return myWebRoots;
  }

  public void setWebRoots(Map<String, Set<String>> webRoots) {
    myWebRoots = webRoots;
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
