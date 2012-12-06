/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.ant.model.impl;

import org.jetbrains.jps.ant.model.JpsAntBuildFileOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class JpsAntBuildFileOptionsImpl implements JpsAntBuildFileOptions {
  private int myMaxStackSize = 2;
  private String myAntCommandLineParameters = "";
  private int myMaxHeapSize = 128;
  private String myCustomJdkName = "";
  private boolean myUseProjectDefaultAnt = true;
  private String myAntInstallationName;
  private List<String> myClasspath = new ArrayList<String>();
  private List<String> myJarDirectories = new ArrayList<String>();

  public void setMaxStackSize(int maxStackSize) {
    myMaxStackSize = maxStackSize;
  }

  @Override
  public void setAntCommandLineParameters(String antCommandLineParameters) {
    myAntCommandLineParameters = antCommandLineParameters;
  }

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

  public void addJarPath(String path) {
    myClasspath.add(path);
  }

  public void addJarDirectory(String directoryPath) {
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
}
