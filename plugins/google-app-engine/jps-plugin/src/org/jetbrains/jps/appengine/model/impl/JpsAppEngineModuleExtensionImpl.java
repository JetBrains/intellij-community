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
package org.jetbrains.jps.appengine.model.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.appengine.model.JpsAppEngineModuleExtension;
import org.jetbrains.jps.appengine.model.PersistenceApi;
import org.jetbrains.jps.incremental.artifacts.impl.JpsArtifactPathUtil;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.List;

/**
 * @author nik
 */
public class JpsAppEngineModuleExtensionImpl extends JpsElementBase<JpsAppEngineModuleExtensionImpl> implements
                                                                                                     JpsAppEngineModuleExtension {
  public static final JpsElementChildRole<JpsAppEngineModuleExtension> ROLE = JpsElementChildRoleBase.create("AppEngine");
  public static final String LIB_APPENGINE_TOOLS_API_JAR = "/lib/appengine-tools-api.jar";
  private final AppEngineModuleExtensionProperties myProperties;

  public JpsAppEngineModuleExtensionImpl(AppEngineModuleExtensionProperties properties) {
    myProperties = properties;
  }

  private JpsAppEngineModuleExtensionImpl(JpsAppEngineModuleExtensionImpl original) {
    myProperties = XmlSerializerUtil.createCopy(original.myProperties);
  }

  public AppEngineModuleExtensionProperties getProperties() {
    return myProperties;
  }

  @Override
  public JpsModule getModule() {
    return (JpsModule)getParent();
  }

  @NotNull
  @Override
  public JpsAppEngineModuleExtensionImpl createCopy() {
    return new JpsAppEngineModuleExtensionImpl(this);
  }

  @Override
  public void applyChanges(@NotNull JpsAppEngineModuleExtensionImpl modified) {
    XmlSerializerUtil.copyBean(modified.myProperties, myProperties);
  }

  @Override
  public String getToolsApiJarPath() {
    return FileUtil.toSystemDependentName(JpsArtifactPathUtil.appendToPath(getSdkHomePath(), LIB_APPENGINE_TOOLS_API_JAR));
  }

  @Override
  public String getOrmLibPath() {
    return FileUtil.toSystemDependentName(JpsArtifactPathUtil.appendToPath(getSdkHomePath(), "/lib/user/orm"));
  }

  @Override
  public String getSdkHomePath() {
    return myProperties.mySdkHomePath;
  }

  @Override
  public boolean isRunEnhancerOnMake() {
    return myProperties.myRunEnhancerOnMake;
  }

  @Override
  public List<String> getFilesToEnhance() {
    return myProperties.myFilesToEnhance;
  }

  @Override
  public PersistenceApi getPersistenceApi() {
    return myProperties.myPersistenceApi;
  }


}
