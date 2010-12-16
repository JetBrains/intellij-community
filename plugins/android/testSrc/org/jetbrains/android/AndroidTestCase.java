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

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdk;
import org.jetbrains.android.sdk.AndroidSdkTestProfile;
import org.jetbrains.android.sdk.EmptySdkLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("android") + "/testData";
  }

  private String getTestSdkPath() {
    return getTestDataPath() + '/' + getTestProfile().getSdkDirName();
  }

  public abstract AndroidSdkTestProfile getTestProfile();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFacet = addAndroidFacet(myModule, getTestProfile(), getTestSdkPath());
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
    tuneModule(moduleBuilder, getTestSdkPath() + getTestProfile().getAndroidJarDirPath(), getTestDataPath(),
               myFixture.getTempDirPath());
  }

  public static void tuneModule(JavaModuleFixtureBuilder moduleBuilder,
                                String androidJarPath,
                                String testDataPath,
                                String moduleDirPath) {
    moduleBuilder.addContentRoot(moduleDirPath);
    moduleBuilder
      .addLibraryJars(ANDROID_LIBRARY_NAME, testDataPath, "android.jar", androidJarPath,
                      "android.jar");

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
    super.tearDown();
    myFacet = null;
  }

  @Nullable
  private static Library findAndroidLibrary(@NotNull Module module) {
    for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        Library library = ((LibraryOrderEntry)entry).getLibrary();
        if (library != null && library.getName().equals(ANDROID_LIBRARY_NAME)) {
          return library;
        }
      }
    }
    return null;
  }

  public static AndroidFacet addAndroidFacet(Module module, AndroidSdkTestProfile testProfile, String sdkPath) {
    FacetManager facetManager = FacetManager.getInstance(module);
    AndroidFacet facet = facetManager.createFacet(AndroidFacet.getFacetType(), "Android", null);
    final AndroidFacetConfiguration configuration = facet.getConfiguration();
    AndroidSdk sdk = AndroidSdk.parse(sdkPath, new EmptySdkLog());
    IAndroidTarget target = sdk.findTargetByName(testProfile.getAndroidTargetName());
    Library androidLibrary = findAndroidLibrary(module);
    configuration.setAndroidPlatform(new AndroidPlatform(sdk, target, androidLibrary));
    final ModifiableFacetModel model = facetManager.createModifiableModel();
    model.addFacet(facet);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        model.commit();
      }
    });
    Disposer.register(module,new Disposable() {
      @Override
      public void dispose() {
        configuration.setAndroidPlatform(null);
      }
    });
    return facet;
  }
}