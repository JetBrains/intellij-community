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
package org.jetbrains.jps.android.model.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.android.util.AndroidNativeLibData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.util.JpsPathUtil;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class JpsAndroidModuleExtensionImpl extends JpsElementBase<JpsAndroidModuleExtensionImpl> implements JpsAndroidModuleExtension {
  public static final JpsElementChildRoleBase<JpsAndroidModuleExtension> KIND = JpsElementChildRoleBase.create("android extension");
  private final JpsAndroidModuleProperties myProperties;
  private final String myBaseModulePath;

  public JpsAndroidModuleExtensionImpl(JpsAndroidModuleProperties properties, String baseModulePath) {
    myProperties = properties;
    myBaseModulePath = baseModulePath;
  }

  @Override
  public JpsModule getModule() {
    return (JpsModule)getParent();
  }

  @NotNull
  @Override
  public JpsAndroidModuleExtensionImpl createCopy() {
    return new JpsAndroidModuleExtensionImpl(XmlSerializerUtil.createCopy(myProperties), myBaseModulePath);
  }

  @Override
  public void applyChanges(@NotNull JpsAndroidModuleExtensionImpl modified) {
    XmlSerializerUtil.copyBean(modified.myProperties, myProperties);
    fireElementChanged();
  }

  @Override
  public String getBaseModulePath() {
    return myBaseModulePath;
  }

  @Override
  public String getCustomDebugKeyStorePath() {
    return JpsPathUtil.urlToPath(myProperties.CUSTOM_DEBUG_KEYSTORE_PATH);
  }

  @Override
  public List<AndroidNativeLibData> getAdditionalNativeLibs() {
    final List<AndroidNativeLibData> libDatas = new ArrayList<AndroidNativeLibData>();
    for (JpsAndroidModuleProperties.AndroidNativeLibData nativeLib : myProperties.myNativeLibs) {
      if (nativeLib.myArchitecture != null && nativeLib.myUrl != null && nativeLib.myTargetFileName != null) {
        libDatas.add(new AndroidNativeLibData(nativeLib.myArchitecture, JpsPathUtil.urlToPath(nativeLib.myUrl), nativeLib.myTargetFileName));
      }
    }
    return libDatas;
  }

  @Override
  public File getResourceDir() throws IOException {
    File resDir = findFileByRelativeModulePath(myProperties.RES_FOLDER_RELATIVE_PATH, true);
    return resDir != null ? resDir.getCanonicalFile() : null;
  }

  @Override
  public File getResourceDirForCompilation() throws IOException {
    File resDir = findFileByRelativeModulePath(myProperties.CUSTOM_APK_RESOURCE_FOLDER, false);
    return resDir != null ? resDir.getCanonicalFile() : null;
  }

  @Override
  public File getManifestFile() throws IOException {
    File manifestFile = findFileByRelativeModulePath(myProperties.MANIFEST_FILE_RELATIVE_PATH, true);
    return manifestFile != null ? manifestFile.getCanonicalFile() : null;
  }

  @Override
  public File getManifestFileForCompilation() throws IOException {
    File manifestFile = findFileByRelativeModulePath(myProperties.CUSTOM_COMPILER_MANIFEST, false);
    return manifestFile != null ? manifestFile.getCanonicalFile() : null;
  }

  @Override
  public File getProguardConfigFile() throws IOException {
    File proguardFile = findFileByRelativeModulePath(myProperties.PROGUARD_CFG_PATH, false);
    return proguardFile != null ? proguardFile.getCanonicalFile() : null;
  }

  @Override
  public File getAssetsDir() throws IOException {
    File manifestFile = findFileByRelativeModulePath(myProperties.ASSETS_FOLDER_RELATIVE_PATH, false);
    return manifestFile != null ? manifestFile.getCanonicalFile() : null;
  }

  public File getAaptGenDir() throws IOException {
    File aaptGenDir = findFileByRelativeModulePath(myProperties.GEN_FOLDER_RELATIVE_PATH_APT, false);
    return aaptGenDir != null ? aaptGenDir.getCanonicalFile() : null;
  }

  public File getAidlGenDir() throws IOException {
    File aidlGenDir = findFileByRelativeModulePath(myProperties.GEN_FOLDER_RELATIVE_PATH_AIDL, false);
    return aidlGenDir != null ? aidlGenDir.getCanonicalFile() : null;
  }

  public JpsAndroidModuleProperties getProperties() {
    return myProperties;
  }

  @Override
  public File getNativeLibsDir() throws IOException {
    File nativeLibsFolder = findFileByRelativeModulePath(myProperties.LIBS_FOLDER_RELATIVE_PATH, false);
    return nativeLibsFolder != null ? nativeLibsFolder.getCanonicalFile() : null;
  }

  @Nullable
  private File findFileByRelativeModulePath(String relativePath, boolean lookInContentRoot) {
    if (relativePath == null || relativePath.length() == 0) {
      return null;
    }

    final JpsModule module = getModule();
    if (myBaseModulePath != null) {
      String absPath = FileUtil.toSystemDependentName(myBaseModulePath + relativePath);
      File f = new File(absPath);

      if (f.exists()) {
        return f;
      }
    }

    if (lookInContentRoot) {
      for (String contentRootUrl : module.getContentRootsList().getUrls()) {
        String absUrl = contentRootUrl + relativePath;
        File f = JpsPathUtil.urlToFile(absUrl);

        if (f.exists()) {
          return f;
        }
      }
    }
    return null;
  }

  @Override
  public boolean isLibrary() {
    return myProperties.LIBRARY_PROJECT;
  }

  @Override
  public boolean useCustomResFolderForCompilation() {
    return myProperties.USE_CUSTOM_APK_RESOURCE_FOLDER;
  }

  @Override
  public boolean useCustomManifestForCompilation() {
    return myProperties.USE_CUSTOM_COMPILER_MANIFEST;
  }

  @Override
  public boolean isPackTestCode() {
    return myProperties.PACK_TEST_CODE;
  }

  @Override
  public boolean isIncludeAssetsFromLibraries() {
    return myProperties.myIncludeAssetsFromLibraries;
  }

  @Override
  public boolean isRunProcessResourcesMavenTask() {
    return myProperties.RUN_PROCESS_RESOURCES_MAVEN_TASK;
  }

  @Override
  public boolean isRunProguard() {
    return myProperties.RUN_PROGUARD;
  }

  @Override
  public boolean isIncludeSystemProguardCfgFile() {
    return myProperties.myIncludeSystemProguardCfgPath;
  }

  @Override
  public String getApkRelativePath() {
    return myProperties.APK_PATH;
  }
}
