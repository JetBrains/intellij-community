/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.android;

import com.android.sdklib.SdkConstants;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.sdk.AndroidSdk;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.sdk.EmptySdkLog;

import java.io.File;
import java.io.IOException;

@SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
public abstract class AndroidTestCase extends JavaCodeInsightFixtureTestCase {
  public static final String ANDROID_LIBRARY_NAME = "Android SDK";

  private boolean myCreateManifest;
  protected AndroidFacet myFacet;

  public AndroidTestCase(boolean createManifest) {
    this.myCreateManifest = createManifest;
  }

  public AndroidTestCase() {
    this(true);
  }

  public static String getAbsoluteTestDataPath() {
    return PathUtil.getCanonicalPath(PluginPathManager.getPluginHomePath("android") + "/testData");
  }

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("android") + "/testData";
  }

  private String getTestSdkPath() {
    return getTestDataPath() + "/sdk1.5";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myFacet = addAndroidFacet(myModule, getTestSdkPath());
    myFixture.copyDirectoryToProject(getResDir(), "res");
    if (myCreateManifest) {
      createManifest();
    }
  }

  protected String getResDir() {
    return "res";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    tuneModule(moduleBuilder,
               myFixture.getTempDirPath());
  }

  public static void tuneModule(JavaModuleFixtureBuilder moduleBuilder, String moduleDirPath) {
    moduleBuilder.addContentRoot(moduleDirPath);

    new File(moduleDirPath + "/src/").mkdir();
    moduleBuilder.addSourceRoot("src");

    new File(moduleDirPath + "/gen/").mkdir();
    moduleBuilder.addSourceRoot("gen");
  }

  protected void createManifest() throws IOException {
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);
  }

  @Override
  public void tearDown() throws Exception {
    myFacet = null;
    super.tearDown();
  }

  public static AndroidFacet addAndroidFacet(Module module, String sdkPath) {
    FacetManager facetManager = FacetManager.getInstance(module);
    AndroidFacet facet = facetManager.createFacet(AndroidFacet.getFacetType(), "Android", null);
    final AndroidFacetConfiguration configuration = facet.getConfiguration();

    addAndroidSdk(module, sdkPath);

    final ModifiableFacetModel facetModel = facetManager.createModifiableModel();
    facetModel.addFacet(facet);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        facetModel.commit();
      }
    });
    return facet;
  }

  private static void addAndroidSdk(Module module, String sdkPath) {
    Sdk androidSdk = createAndroidSdk(sdkPath);
    final ModifiableRootModel moduleModel = ModuleRootManager.getInstance(module).getModifiableModel();
    moduleModel.setSdk(androidSdk);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        moduleModel.commit();
      }
    });
  }

  private static Sdk createAndroidSdk(String sdkPath) {
    Sdk sdk = ProjectJdkTable.getInstance().createSdk("android_test_sdk", AndroidSdkType.getInstance());
    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.setHomePath(sdkPath);

    String androidJarPath = sdkPath + "/../android.jar!/";
    VirtualFile androidJar = JarFileSystem.getInstance().findFileByPath(androidJarPath);
    sdkModificator.addRoot(androidJar, OrderRootType.CLASSES);

    AndroidSdkAdditionalData data = new AndroidSdkAdditionalData(sdk);
    AndroidSdk sdkObject = AndroidSdk.parse(sdkPath, new EmptySdkLog());
    data.setBuildTarget(sdkObject.findTargetByName("Android 1.5"));
    sdkModificator.setSdkAdditionalData(data);
    sdkModificator.commitChanges();
    return sdk;
  }
}