/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.model.build;

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REFERENCE_RESOLUTION_RESOLVE_OTHER_PROJECT_PATH;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REFERENCE_RESOLUTION_RESOLVE_OTHER_PROJECT_PATH_SUB;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REFERENCE_RESOLUTION_RESOLVE_PARENT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REFERENCE_RESOLUTION_RESOLVE_PARENT_SUB;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REFERENCE_RESOLUTION_RESOLVE_PROJECT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REFERENCE_RESOLUTION_RESOLVE_PROJECT_DIR_SUB;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REFERENCE_RESOLUTION_RESOLVE_PROJECT_PATH_SUB;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REFERENCE_RESOLUTION_RESOLVE_ROOT_DIR_SUB;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REFERENCE_RESOLUTION_RESOLVE_ROOT_PROJECT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.REFERENCE_RESOLUTION_RESOLVE_ROOT_PROJECT_SUB;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING;

import com.android.tools.idea.gradle.dsl.GradleUtil;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import org.junit.Test;

import java.io.File;

/**
 * Tests resolving references to project, parent, rootProject etc.
 */
public class ReferenceResolutionTest extends GradleFileModelTestCase {
  @Test
  public void testResolveRootDir() throws Exception {
    writeToSettingsFile(getSubModuleSettingsText());
    writeToBuildFile("");
    writeToSubModuleBuildFile(REFERENCE_RESOLUTION_RESOLVE_ROOT_DIR_SUB);

    String expectedRootDir = GradleUtil.getBaseDirPath(myProject).getPath();
    ExtModel ext = getSubModuleGradleBuildModel().ext();
    verifyPropertyModel(ext.findProperty("rpd").resolve(), STRING_TYPE, expectedRootDir, STRING, PropertyType.REGULAR, 1);
    verifyPropertyModel(ext.findProperty("rpd1").resolve(), STRING_TYPE, expectedRootDir, STRING, PropertyType.REGULAR, 1);
    verifyPropertyModel(ext.findProperty("rpd2").resolve(), STRING_TYPE, expectedRootDir, STRING, PropertyType.REGULAR, 1);
    verifyPropertyModel(ext.findProperty("rpd3").resolve(), STRING_TYPE, expectedRootDir, STRING, PropertyType.REGULAR, 1);
    verifyPropertyModel(ext.findProperty("rpd4").resolve(), STRING_TYPE, expectedRootDir, STRING, PropertyType.REGULAR, 1);
    verifyPropertyModel(ext.findProperty("rpd5").resolve(), STRING_TYPE, expectedRootDir, STRING, PropertyType.REGULAR, 1);
  }

  @Test
  public void testResolveProjectDir() throws Exception {
    writeToSettingsFile(getSubModuleSettingsText());
    writeToBuildFile("");
    writeToSubModuleBuildFile(REFERENCE_RESOLUTION_RESOLVE_PROJECT_DIR_SUB);

    String expectedRootDir = GradleUtil.getBaseDirPath(myProject).getPath();
    String expectedSubModuleDir = toSystemDependentPath(mySubModuleBuildFile.getParent().getPath());
    ExtModel ext = getSubModuleGradleBuildModel().ext();
    verifyPropertyModel(ext.findProperty("pd").resolve(), STRING_TYPE, expectedSubModuleDir, STRING, PropertyType.REGULAR, 1);
    verifyPropertyModel(ext.findProperty("pd1").resolve(), STRING_TYPE, expectedSubModuleDir, STRING, PropertyType.REGULAR, 1);
    verifyPropertyModel(ext.findProperty("pd2").resolve(), STRING_TYPE, expectedRootDir, STRING, PropertyType.REGULAR, 1);
    verifyPropertyModel(ext.findProperty("pd3").resolve(), STRING_TYPE, expectedRootDir, STRING, PropertyType.REGULAR, 1);
    verifyPropertyModel(ext.findProperty("pd4").resolve(), STRING_TYPE, expectedSubModuleDir, STRING, PropertyType.REGULAR, 1);
    verifyPropertyModel(ext.findProperty("pd5").resolve(), STRING_TYPE, expectedRootDir, STRING, PropertyType.REGULAR, 1);
  }

  private static String toSystemDependentPath(String path) {
    if (File.separatorChar != '/') {
      return path.replace('/', File.separatorChar);
    }
    return path;
  }

  @Test
  public void testResolveProject() throws Exception {
    writeToSettingsFile(getSubModuleSettingsText());
    writeToBuildFile("");
    writeToSubModuleBuildFile(REFERENCE_RESOLUTION_RESOLVE_PROJECT);

    AndroidModel android = getSubModuleGradleBuildModel().android();
    assertNotNull(android);

    assertEquals("compileSdkVersion", "android-23", android.compileSdkVersion());
    assertEquals("applicationIdSuffix", "android-23", android.defaultConfig().applicationIdSuffix());
  }

  @Test
  public void testResolveParent() throws Exception {
    writeToSettingsFile(getSubModuleSettingsText());
    writeToBuildFile(REFERENCE_RESOLUTION_RESOLVE_PARENT);
    writeToSubModuleBuildFile(REFERENCE_RESOLUTION_RESOLVE_PARENT_SUB);

    AndroidModel androidModel = getGradleBuildModel().android();
    assertNotNull(androidModel);
    assertEquals("compileSdkVersion", "android-23", androidModel.compileSdkVersion());

    AndroidModel subModuleAndroidModel = getSubModuleGradleBuildModel().android();
    assertNotNull(subModuleAndroidModel);
    assertEquals("compileSdkVersion", "android-23", subModuleAndroidModel.compileSdkVersion());
  }

  @Test
  public void testResolveRootProject() throws Exception {
    writeToSettingsFile(getSubModuleSettingsText());
    writeToBuildFile(REFERENCE_RESOLUTION_RESOLVE_ROOT_PROJECT);
    writeToSubModuleBuildFile(REFERENCE_RESOLUTION_RESOLVE_ROOT_PROJECT_SUB);

    AndroidModel androidModel = getGradleBuildModel().android();
    assertNotNull(androidModel);
    assertEquals("compileSdkVersion", "android-23", androidModel.compileSdkVersion());

    AndroidModel subModuleAndroidModel = getSubModuleGradleBuildModel().android();
    assertNotNull(subModuleAndroidModel);
    assertEquals("compileSdkVersion", "android-23", subModuleAndroidModel.compileSdkVersion());
  }

  @Test
  public void testResolveProjectPath() throws Exception {
    writeToSettingsFile(getSubModuleSettingsText());
    writeToBuildFile("");
    writeToSubModuleBuildFile(REFERENCE_RESOLUTION_RESOLVE_PROJECT_PATH_SUB);

    AndroidModel android = getSubModuleGradleBuildModel().android();
    assertNotNull(android);

    assertEquals("compileSdkVersion", "android-23", android.compileSdkVersion());
    assertEquals("minSdkVersion", "android-23", android.defaultConfig().minSdkVersion());
  }

  @Test
  public void testResolveOtherProjectPath() throws Exception {
    writeToSettingsFile(getSubModuleSettingsText());
    writeToBuildFile(REFERENCE_RESOLUTION_RESOLVE_OTHER_PROJECT_PATH);
    writeToSubModuleBuildFile(REFERENCE_RESOLUTION_RESOLVE_OTHER_PROJECT_PATH_SUB);

    AndroidModel androidModel = getGradleBuildModel().android();
    assertNotNull(androidModel);
    assertEquals("compileSdkVersion", "android-23", androidModel.compileSdkVersion());

    AndroidModel subModuleAndroidModel = getSubModuleGradleBuildModel().android();
    assertNotNull(subModuleAndroidModel);
    assertEquals("compileSdkVersion", "android-23", subModuleAndroidModel.compileSdkVersion());
  }
}
