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

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.ALL_PROJECTS_ALL_PROJECTS_SECTION;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.ALL_PROJECTS_OVERRIDE_ALL_PROJECTS_SECTION;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.ALL_PROJECTS_OVERRIDE_ALL_PROJECTS_SECTION_IN_SUBPROJECT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.ALL_PROJECTS_OVERRIDE_ALL_PROJECTS_SECTION_IN_SUBPROJECT_SUB;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.ALL_PROJECTS_OVERRIDE_WITH_ALL_PROJECTS_SECTION;

import com.android.tools.idea.gradle.dsl.api.java.JavaModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.intellij.pom.java.LanguageLevel;
import org.junit.Test;

/**
 * Tests allprojects section of the build.gradle file.
 */
public class AllProjectsTest extends GradleFileModelTestCase {
  @Test
  public void testAllProjectsSection() throws Exception {
    writeToSettingsFile(getSubModuleSettingsText());
    writeToBuildFile(ALL_PROJECTS_ALL_PROJECTS_SECTION);
    writeToSubModuleBuildFile("");

    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility().toLanguageLevel());

    JavaModel subModuleJavaModel = getSubModuleGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, subModuleJavaModel.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_6, subModuleJavaModel.targetCompatibility().toLanguageLevel());
  }

  @Test
  public void testOverrideWithAllProjectsSection() throws Exception {
    writeToSettingsFile(getSubModuleSettingsText());
    writeToBuildFile(ALL_PROJECTS_OVERRIDE_WITH_ALL_PROJECTS_SECTION);
    writeToSubModuleBuildFile("");

    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility().toLanguageLevel()); // 1_4 is overridden with 1_5
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility().toLanguageLevel()); // 1_5 is overridden with 1_6

    JavaModel subModuleJavaModel = getSubModuleGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5,
                 subModuleJavaModel.sourceCompatibility().toLanguageLevel()); // Subproject got 1_5 from allprojects section
    assertEquals(LanguageLevel.JDK_1_6,
                 subModuleJavaModel.targetCompatibility().toLanguageLevel()); // Subproject got 1_5 from allprojects section
  }

  @Test
  public void testOverrideAllProjectsSection() throws Exception {
    writeToSettingsFile(getSubModuleSettingsText());
    writeToBuildFile(ALL_PROJECTS_OVERRIDE_ALL_PROJECTS_SECTION);
    writeToSubModuleBuildFile("");

    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility().toLanguageLevel()); // 1_4 is overridden with 1_5
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility().toLanguageLevel()); // 1_5 is overridden with 1_6

    JavaModel subModuleJavaModel = getSubModuleGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_4,
                 subModuleJavaModel.sourceCompatibility().toLanguageLevel()); // Subproject got 1_4 from allprojects section
    assertEquals(LanguageLevel.JDK_1_5,
                 subModuleJavaModel.targetCompatibility().toLanguageLevel()); // Subproject got 1_5 from allprojects section
  }

  @Test
  public void testOverrideAllProjectsSectionInSubproject() throws Exception {
    writeToSettingsFile(getSubModuleSettingsText());
    writeToBuildFile(ALL_PROJECTS_OVERRIDE_ALL_PROJECTS_SECTION_IN_SUBPROJECT);
    writeToSubModuleBuildFile(ALL_PROJECTS_OVERRIDE_ALL_PROJECTS_SECTION_IN_SUBPROJECT_SUB);

    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility().toLanguageLevel()); // 1_4 is overridden with 1_5
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility().toLanguageLevel()); // 1_5 is overridden with 1_6

    JavaModel subModuleJavaModel = getSubModuleGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_6, subModuleJavaModel.sourceCompatibility().toLanguageLevel()); // 1_4 is overridden with 1_6
    assertEquals(LanguageLevel.JDK_1_7, subModuleJavaModel.targetCompatibility().toLanguageLevel()); // 1_5 is overridden with 1_7
  }
}
