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

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.android.util.AndroidCommonUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class JpsAndroidModuleProperties {
  public String GEN_FOLDER_RELATIVE_PATH_APT;
  public String GEN_FOLDER_RELATIVE_PATH_AIDL;

  public String MANIFEST_FILE_RELATIVE_PATH;

  public String RES_FOLDER_RELATIVE_PATH;
  public String ASSETS_FOLDER_RELATIVE_PATH;
  public String LIBS_FOLDER_RELATIVE_PATH;

  public boolean USE_CUSTOM_APK_RESOURCE_FOLDER;
  public String CUSTOM_APK_RESOURCE_FOLDER = "";

  public boolean USE_CUSTOM_COMPILER_MANIFEST;
  public String CUSTOM_COMPILER_MANIFEST = "";

  public String APK_PATH = "";

  public boolean LIBRARY_PROJECT;

  public boolean RUN_PROCESS_RESOURCES_MAVEN_TASK = true;

  public boolean GENERATE_UNSIGNED_APK;

  public String CUSTOM_DEBUG_KEYSTORE_PATH = "";

  public boolean PACK_TEST_CODE;

  public boolean RUN_PROGUARD;
  public String PROGUARD_CFG_PATH;

  @Tag(AndroidCommonUtils.INCLUDE_SYSTEM_PROGUARD_FILE_ELEMENT_NAME)
  public boolean myIncludeSystemProguardCfgPath = true;

  @Tag(AndroidCommonUtils.INCLUDE_ASSETS_FROM_LIBRARIES_ELEMENT_NAME)
  public boolean myIncludeAssetsFromLibraries = false;

  @Tag("resOverlayFolders")
  @AbstractCollection(surroundWithTag = false, elementTag = "path", elementValueAttribute = "")
  public List<String> RES_OVERLAY_FOLDERS = new ArrayList<String>();

  @Tag(AndroidCommonUtils.ADDITIONAL_NATIVE_LIBS_ELEMENT)
  @AbstractCollection(surroundWithTag = false)
  public List<AndroidNativeLibData> myNativeLibs = new ArrayList<AndroidNativeLibData>();

  @Tag(AndroidCommonUtils.ITEM_ELEMENT)
  public static class AndroidNativeLibData {
    @Attribute(AndroidCommonUtils.ARCHITECTURE_ATTRIBUTE)
    public String myArchitecture;
    @Attribute(AndroidCommonUtils.URL_ATTRIBUTE)
    public String myUrl;
    @Attribute(AndroidCommonUtils.TARGET_FILE_NAME_ATTRIBUTE)
    public String myTargetFileName;
  }
}
