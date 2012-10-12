/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.facet;

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidNativeLibData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidFacetConfiguration implements FacetConfiguration {
  @NonNls private static final String RES_OVERLAY_FOLDERS_ELEMENT_NAME = "resOverlayFolders";
  @NonNls private static final String PATH_ELEMENT_NAME = "path";

  @NonNls private static final String INCLUDE_ASSETS_FROM_LIBRARIES_ELEMENT_NAME = "includeAssetsFromLibraries";
  @NonNls private static final String INCLUDE_SYSTEM_PROGUARD_FILE_ELEMENT_NAME = "includeSystemProguardFile";

  public String GEN_FOLDER_RELATIVE_PATH_APT = "/" + SdkConstants.FD_GEN_SOURCES;
  public String GEN_FOLDER_RELATIVE_PATH_AIDL = "/" + SdkConstants.FD_GEN_SOURCES;

  public String MANIFEST_FILE_RELATIVE_PATH = "/" + SdkConstants.FN_ANDROID_MANIFEST_XML;

  public String RES_FOLDER_RELATIVE_PATH = "/" + SdkConstants.FD_RES;
  public String ASSETS_FOLDER_RELATIVE_PATH = "/" + SdkConstants.FD_ASSETS;
  public String LIBS_FOLDER_RELATIVE_PATH = "/" + SdkConstants.FD_NATIVE_LIBS;

  private boolean myIncludeAssetsFromLibraries = false;

  public List<String> RES_OVERLAY_FOLDERS = Arrays.asList("/res-overlay");

  public boolean REGENERATE_R_JAVA = true;

  public boolean REGENERATE_JAVA_BY_AIDL = true;

  public boolean USE_CUSTOM_APK_RESOURCE_FOLDER = false;
  public String CUSTOM_APK_RESOURCE_FOLDER = "";

  public boolean USE_CUSTOM_COMPILER_MANIFEST = false;
  public String CUSTOM_COMPILER_MANIFEST = "";

  public String APK_PATH = "";

  public boolean LIBRARY_PROJECT = false;

  public boolean RUN_PROCESS_RESOURCES_MAVEN_TASK = true;

  public boolean GENERATE_UNSIGNED_APK = false;

  public String CUSTOM_DEBUG_KEYSTORE_PATH = "";

  public boolean PACK_TEST_CODE = false;

  public boolean RUN_PROGUARD = false;
  public String PROGUARD_CFG_PATH = "/" + AndroidCompileUtil.PROGUARD_CFG_FILE_NAME;

  private boolean myIncludeSystemProguardCfgPath = true;

  private List<AndroidNativeLibData> myAdditionalNativeLibraries = Collections.emptyList();

  private AndroidFacet myFacet = null;

  public void init(@NotNull Module module, @NotNull VirtualFile contentRoot) {
    init(module, contentRoot.getPath());
  }

  public void init(@NotNull Module module, @NotNull String baseDirectoryPath) {
    final String s = AndroidRootUtil.getPathRelativeToModuleDir(module, baseDirectoryPath);
    if (s == null || s.length() == 0) {
      return;
    }

    GEN_FOLDER_RELATIVE_PATH_APT = '/' + s + GEN_FOLDER_RELATIVE_PATH_APT;
    GEN_FOLDER_RELATIVE_PATH_AIDL = '/' + s + GEN_FOLDER_RELATIVE_PATH_AIDL;
    MANIFEST_FILE_RELATIVE_PATH = '/' + s + MANIFEST_FILE_RELATIVE_PATH;
    RES_FOLDER_RELATIVE_PATH = '/' + s + RES_FOLDER_RELATIVE_PATH;
    ASSETS_FOLDER_RELATIVE_PATH = '/' + s + ASSETS_FOLDER_RELATIVE_PATH;
    LIBS_FOLDER_RELATIVE_PATH = '/' + s + LIBS_FOLDER_RELATIVE_PATH;

    for (int i = 0; i < RES_OVERLAY_FOLDERS.size(); i++) {
      RES_OVERLAY_FOLDERS.set(i, '/' + s + RES_OVERLAY_FOLDERS.get(i));
    }
  }

  @Nullable
  public AndroidPlatform getAndroidPlatform() {
    Sdk moduleSdk = ModuleRootManager.getInstance(myFacet.getModule()).getSdk();
    if (moduleSdk != null && moduleSdk.getSdkType().equals(AndroidSdkType.getInstance())) {
      AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)moduleSdk.getSdkAdditionalData();
      return data != null ? data.getAndroidPlatform() : null;
    }
    return null;
  }

  @Nullable
  public AndroidSdkData getAndroidSdk() {
    AndroidPlatform platform = getAndroidPlatform();
    return platform != null ? platform.getSdkData() : null;
  }

  @Nullable
  public IAndroidTarget getAndroidTarget() {
    AndroidPlatform platform = getAndroidPlatform();
    return platform != null ? platform.getTarget() : null;
  }

  public void setFacet(@NotNull AndroidFacet facet) {
    this.myFacet = facet;
    facet.androidPlatformChanged();
  }

  public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    return new FacetEditorTab[]{new AndroidFacetEditorTab(editorContext, this)};
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    readResOverlayFolders(element);

    final Element additionalNativeLibsElement = element.getChild(AndroidCommonUtils.ADDITIONAL_NATIVE_LIBS_ELEMENT);
    myAdditionalNativeLibraries = new ArrayList<AndroidNativeLibData>();

    if (additionalNativeLibsElement != null) {
      for (Object child : additionalNativeLibsElement.getChildren()) {
        final Element childElement = (Element)child;
        final String architecture = childElement.getAttributeValue(AndroidCommonUtils.ARCHITECTURE_ATTRIBUTE);
        final String url = childElement.getAttributeValue(AndroidCommonUtils.URL_ATTRIBUTE);
        final String targetFileName = childElement.getAttributeValue(AndroidCommonUtils.TARGET_FILE_NAME_ATTRIBUTE);

        if (url != null && architecture != null && targetFileName != null) {
          myAdditionalNativeLibraries.add(new AndroidNativeLibData(architecture, VfsUtil.urlToPath(url), targetFileName));
        }
      }
    }

    final Element includeSystemProguardFile = element.getChild(INCLUDE_SYSTEM_PROGUARD_FILE_ELEMENT_NAME);
    final String includeSystemProguardFileValue = includeSystemProguardFile != null
                                                  ? includeSystemProguardFile.getValue()
                                                  : null;
    myIncludeSystemProguardCfgPath = includeSystemProguardFileValue != null &&
                                     Boolean.parseBoolean(includeSystemProguardFileValue);

    final Element includeAssetsFromLibraries = element.getChild(INCLUDE_ASSETS_FROM_LIBRARIES_ELEMENT_NAME);
    final String includeAssetsFromLibrariesValue = includeAssetsFromLibraries != null
                                                   ? includeAssetsFromLibraries.getValue()
                                                   : null;
    myIncludeAssetsFromLibraries = includeAssetsFromLibrariesValue == null ||
                                   Boolean.parseBoolean(includeAssetsFromLibrariesValue);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    writeResOverlayFolders(element);

    final Element includeSystemProguerdFile = new Element(INCLUDE_SYSTEM_PROGUARD_FILE_ELEMENT_NAME);
    includeSystemProguerdFile.setText(Boolean.toString(myIncludeSystemProguardCfgPath));
    element.addContent(includeSystemProguerdFile);

    final Element additionalNativeLibs = new Element(AndroidCommonUtils.ADDITIONAL_NATIVE_LIBS_ELEMENT);

    for (AndroidNativeLibData lib : myAdditionalNativeLibraries) {
      final Element item = new Element(AndroidCommonUtils.ITEM_ELEMENT);
      item.setAttribute(AndroidCommonUtils.ARCHITECTURE_ATTRIBUTE, lib.getArchitecture());
      item.setAttribute(AndroidCommonUtils.URL_ATTRIBUTE, VfsUtil.pathToUrl(lib.getPath()));
      item.setAttribute(AndroidCommonUtils.TARGET_FILE_NAME_ATTRIBUTE, lib.getTargetFileName());
      additionalNativeLibs.addContent(item);
    }
    element.addContent(additionalNativeLibs);
  }

  private void readResOverlayFolders(final Element element) throws InvalidDataException {
    final List<String> resOverlayFolders = new ArrayList<String>();
    final Element resOverlayFoldersElement = element.getChild(RES_OVERLAY_FOLDERS_ELEMENT_NAME);

    if (resOverlayFoldersElement != null) {
      //noinspection unchecked
      for (Element conditionalCompilerDefinitionElement : (Iterable<Element>)resOverlayFoldersElement.getChildren(PATH_ELEMENT_NAME)) {
        resOverlayFolders.add(conditionalCompilerDefinitionElement.getValue());
      }
    }
    RES_OVERLAY_FOLDERS = resOverlayFolders;
  }

  private void writeResOverlayFolders(final Element element) throws WriteExternalException {
    final Element resOverlayFoldersElement = new Element(RES_OVERLAY_FOLDERS_ELEMENT_NAME);

    for (String resOverlayFolderPath : RES_OVERLAY_FOLDERS) {
      final Element pathElement = new Element(PATH_ELEMENT_NAME);
      pathElement.setText(resOverlayFolderPath);
      resOverlayFoldersElement.addContent(pathElement);
    }
    element.addContent(resOverlayFoldersElement);
  }

  public boolean isIncludeSystemProguardCfgPath() {
    return myIncludeSystemProguardCfgPath;
  }

  public void setIncludeSystemProguardCfgPath(boolean includeSystemProguardCfgPath) {
    myIncludeSystemProguardCfgPath = includeSystemProguardCfgPath;
  }

  @NotNull
  public List<AndroidNativeLibData> getAdditionalNativeLibraries() {
    return myAdditionalNativeLibraries;
  }

  public void setAdditionalNativeLibraries(@NotNull List<AndroidNativeLibData> additionalNativeLibraries) {
    myAdditionalNativeLibraries = additionalNativeLibraries;
  }

  public boolean isIncludeAssetsFromLibraries() {
    return myIncludeAssetsFromLibraries;
  }

  public void setIncludeAssetsFromLibraries(boolean includeAssetsFromLibraries) {
    myIncludeAssetsFromLibraries = includeAssetsFromLibraries;
  }
}
