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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ant.model.JpsAntInstallation;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.ex.JpsElementCollectionRole;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class JpsAntInstallationImpl extends JpsElementBase<JpsAntInstallationImpl> implements JpsAntInstallation {
  private final File myAntHome;
  private final String myName;
  private final List<String> myClasspath;
  private final List<String> myJarDirectories;
  private static final JpsElementChildRoleBase<JpsAntInstallation> ROLE = JpsElementChildRoleBase.create("ant installation");
  public static final JpsElementCollectionRole<JpsAntInstallation> COLLECTION_ROLE = JpsElementCollectionRole.create(ROLE);

  public JpsAntInstallationImpl(File antHome, String name, final List<String> classpath, List<String> jarDirectories) {
    myAntHome = antHome;
    myName = name;
    myClasspath = classpath;
    myJarDirectories = jarDirectories;
  }

  @NotNull
  @Override
  public JpsAntInstallationImpl createCopy() {
    return new JpsAntInstallationImpl(myAntHome, myName, myClasspath, myJarDirectories);
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public void applyChanges(@NotNull JpsAntInstallationImpl modified) {
  }

  @Override
  public File getAntHome() {
    return myAntHome;
  }

  @Override
  public List<String> getClasspath() {
    return getClasspath(myClasspath, myJarDirectories);
  }

  public static List<String> getClasspath(final List<String> classpath, final List<String> jarDirectories) {
    List<String> result = new ArrayList<String>(classpath);
    for (String directory : jarDirectories) {
      addAllJarsFromDirectory(result, new File(directory));
    }
    return result;
  }

  public static void addAllJarsFromDirectory(List<String> classpath, final File dir) {
    File[] files = dir.listFiles();
    if (files != null) {
      for (File file : files) {
        if (StringUtil.endsWithIgnoreCase(file.getName(), ".jar")) {
          classpath.add(file.getAbsolutePath());
        }
      }
    }
  }
}
