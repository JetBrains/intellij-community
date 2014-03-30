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

import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ant.model.JpsAntBuildFileOptions;
import org.jetbrains.jps.ant.model.JpsAntConfiguration;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;

import java.util.Collection;
import java.util.Map;

/**
 * @author nik
 */
public class JpsAntConfigurationImpl extends JpsElementBase<JpsAntConfigurationImpl> implements JpsAntConfiguration {
  public static final JpsElementChildRole<JpsAntConfiguration> ROLE = JpsElementChildRoleBase.create("ant configuration");
  private String myProjectDefaultAntName;
  private final Map<String, JpsAntBuildFileOptions> myOptionsMap = new HashMap<String, JpsAntBuildFileOptions>();

  public JpsAntConfigurationImpl(Map<String, JpsAntBuildFileOptions> options, String projectDefaultAntName) {
    myProjectDefaultAntName = projectDefaultAntName;
    myOptionsMap.putAll(options);
  }

  @NotNull
  @Override
  public JpsAntConfigurationImpl createCopy() {
    return new JpsAntConfigurationImpl(myOptionsMap, myProjectDefaultAntName);
  }

  @Override
  public void setProjectDefaultAntName(@Nullable String projectDefaultAntName) {
    myProjectDefaultAntName = projectDefaultAntName;
  }

  @Override
  @Nullable
  public String getProjectDefaultAntName() {
    return myProjectDefaultAntName;
  }

  @Override
  public void applyChanges(@NotNull JpsAntConfigurationImpl modified) {
  }

  @Override
  @NotNull
  public Collection<JpsAntBuildFileOptions> getOptionsForAllBuildFiles() {
    return myOptionsMap.values();
  }

  @NotNull
  @Override
  public JpsAntBuildFileOptions getOptions(@NotNull String buildFileUrl) {
    JpsAntBuildFileOptions options = myOptionsMap.get(buildFileUrl);
    if (options != null) {
      return options;
    }
    return new JpsAntBuildFileOptionsImpl();
  }
}
