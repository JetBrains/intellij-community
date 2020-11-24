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
package com.android.tools.idea.gradle.dsl.model;

import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

import com.android.tools.idea.gradle.dsl.GradleUtil;
import com.android.tools.idea.gradle.dsl.TestFileName;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemDependent;
import org.junit.Test;

/**
 * Tests for {@link GradleSettingsModel}.
 */
public class GradleSettingsModelTest extends GradleFileModelTestCase {
  @Test
  public void testIncludedModulePaths() throws Exception {
    writeToSettingsFile(TestFile.INCLUDED_MODULE_PATHS);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals("include", ImmutableList.of(":", ":app", ":lib", ":lib:subLib"), settingsModel.modulePaths());
  }

  @Test
  public void testIncludedModulePathsWithDotSeparator() throws Exception {
    writeToBuildFile(TestFile.INCLUDED_MODULE_PATHS_WITH_DOT_SEPARATOR);
    writeToSettingsFile(TestFile.INCLUDED_MODULE_PATHS_WITH_DOT_SEPARATOR_SETTINGS);
    Module newModule = writeToNewSubModule("app.ext", "", "");

    ProjectBuildModel projectModel = getProjectBuildModel();
    GradleBuildModel buildModel = projectModel.getModuleBuildModel(newModule);
    assertNotNull(buildModel);
  }

  @Test
  public void testAddAndResetModulePaths() throws Exception {
    writeToSettingsFile(TestFile.ADD_AND_RESET_MODULE_PATHS);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals("include", ImmutableList.of(":", ":app", ":lib"), settingsModel.modulePaths());

    settingsModel.addModulePath("lib1");
    assertEquals("include", ImmutableList.of(":", ":app", ":lib", ":lib1"), settingsModel.modulePaths());

    settingsModel.resetState();
    assertEquals("include", ImmutableList.of(":", ":app", ":lib"), settingsModel.modulePaths());
  }

  @Test
  public void testAddAndApplyModulePaths() throws Exception {
    writeToSettingsFile(TestFile.ADD_AND_APPLY_MODULE_PATHS);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals("include", ImmutableList.of(":", ":app", ":lib"), settingsModel.modulePaths());

    settingsModel.addModulePath("lib1");
    assertEquals("include", ImmutableList.of(":", ":app", ":lib", ":lib1"), settingsModel.modulePaths());

    applyChanges(settingsModel);
    assertEquals("include", ImmutableList.of(":", ":app", ":lib", ":lib1"), settingsModel.modulePaths());

    settingsModel.reparse();
    assertEquals("include", ImmutableList.of(":", ":lib1", ":app", ":lib"), settingsModel.modulePaths());

    verifyFileContents(mySettingsFile, TestFile.ADD_AND_APPLY_MODULE_PATHS_EXPECTED);
  }

  @Test
  public void testAddAndApplyAllModulePaths() throws Exception {
    writeToSettingsFile(TestFile.ADD_AND_APPLY_ALL_MODULE_PATHS);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals("include", ImmutableList.of(":"), settingsModel.modulePaths());

    settingsModel.addModulePath("app");
    assertEquals("include", ImmutableList.of(":", ":app"), settingsModel.modulePaths());

    applyChanges(settingsModel);
    assertEquals("include", ImmutableList.of(":", ":app"), settingsModel.modulePaths());

    settingsModel.reparse();
    assertEquals("include", ImmutableList.of(":", ":app"), settingsModel.modulePaths());

    verifyFileContents(mySettingsFile, TestFile.ADD_AND_APPLY_ALL_MODULE_PATHS_EXPECTED);
  }

  @Test
  public void testRemoveAndResetModulePaths() throws Exception {
    writeToSettingsFile(TestFile.REMOVE_AND_RESET_MODULE_PATHS);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals("include", ImmutableList.of(":", ":app", ":lib"), settingsModel.modulePaths());

    settingsModel.removeModulePath(":app");
    assertEquals("include", ImmutableList.of(":", ":lib"), settingsModel.modulePaths());

    settingsModel.resetState();
    assertEquals("include", ImmutableList.of(":", ":app", ":lib"), settingsModel.modulePaths());
  }

  @Test
  public void testRemoveAndApplyModulePaths() throws Exception {
    writeToSettingsFile(TestFile.REMOVE_AND_APPLY_MODULE_PATHS);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals("include", ImmutableList.of(":", ":app", ":lib"), settingsModel.modulePaths());

    settingsModel.removeModulePath(":app");
    assertEquals("include", ImmutableList.of(":", ":lib"), settingsModel.modulePaths());

    applyChanges(settingsModel);
    assertEquals("include", ImmutableList.of(":", ":lib"), settingsModel.modulePaths());

    settingsModel.reparse();
    assertEquals("include", ImmutableList.of(":", ":lib"), settingsModel.modulePaths());

    verifyFileContents(mySettingsFile, TestFile.REMOVE_AND_APPLY_MODULE_PATHS_EXPECTED);
  }

  @Test
  public void testRemoveAndApplyAllModulePaths() throws Exception {
    writeToSettingsFile(TestFile.REMOVE_AND_APPLY_ALL_MODULE_PATHS);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals("include", ImmutableList.of(":", ":app", ":lib", ":lib1"), settingsModel.modulePaths());

    settingsModel.removeModulePath(":app");
    settingsModel.removeModulePath("lib");
    settingsModel.removeModulePath(":lib1");
    assertEquals("include", ImmutableList.of(":"), settingsModel.modulePaths());

    applyChanges(settingsModel);
    assertEquals("include", ImmutableList.of(":"), settingsModel.modulePaths());

    settingsModel.reparse();
    assertEquals("include", ImmutableList.of(":"), settingsModel.modulePaths());

    verifyFileContents(mySettingsFile, TestFile.REMOVE_AND_APPLY_ALL_MODULE_PATHS_EXPECTED);
  }

  @Test
  public void testReplaceAndResetModulePaths() throws Exception {
    writeToSettingsFile(TestFile.REPLACE_AND_RESET_MODULE_PATHS);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals("include", ImmutableList.of(":", ":app", ":lib", ":lib:subLib"), settingsModel.modulePaths());

    settingsModel.replaceModulePath("lib", "lib1");
    assertEquals("include", ImmutableList.of(":", ":app", ":lib1", ":lib:subLib"), settingsModel.modulePaths());

    settingsModel.resetState();
    assertEquals("include", ImmutableList.of(":", ":app", ":lib", ":lib:subLib"), settingsModel.modulePaths());
  }

  @Test
  public void testReplaceAndApplyModulePaths() throws Exception {
    writeToSettingsFile(TestFile.REPLACE_AND_APPLY_MODULE_PATHS);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals("include", ImmutableList.of(":", ":app", ":lib", ":lib:subLib"), settingsModel.modulePaths());

    settingsModel.replaceModulePath("lib", "lib1");
    assertEquals("include", ImmutableList.of(":", ":app", ":lib1", ":lib:subLib"), settingsModel.modulePaths());

    applyChanges(settingsModel);
    assertEquals("include", ImmutableList.of(":", ":app", ":lib1", ":lib:subLib"), settingsModel.modulePaths());

    settingsModel.reparse();
    assertEquals("include", ImmutableList.of(":", ":app", ":lib1", ":lib:subLib"), settingsModel.modulePaths());

    verifyFileContents(mySettingsFile, TestFile.REPLACE_AND_APPLY_MODULE_PATHS_EXPECTED);
  }

  @Test
  public void testGetModuleDirectory() throws Exception {
    writeToSettingsFile(TestFile.GET_MODULE_DIRECTORY);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals(ImmutableList.of(":", ":app", ":libs", ":libs:mylibrary", ":olibs", ":olibs:mylibrary", ":notamodule:deepmodule"),
                 settingsModel.modulePaths());

    File rootDir = GradleUtil.getBaseDirPath(myProject);
    assertEquals(rootDir, settingsModel.moduleDirectory(":"));
    assertEquals(new File(rootDir, "app"), settingsModel.moduleDirectory("app"));
    assertEquals(new File(rootDir, "libs"), settingsModel.moduleDirectory(":libs"));
    assertEquals(new File(rootDir, "xyz/mylibrary"), settingsModel.moduleDirectory(":libs:mylibrary"));
    assertEquals(new File(rootDir, "otherlibs"), settingsModel.moduleDirectory("olibs"));
    assertEquals(new File(rootDir, "otherlibs/mylibrary"), settingsModel.moduleDirectory(":olibs:mylibrary"));
    assertEquals(new File(rootDir, "notamodule/deepmodule"), settingsModel.moduleDirectory(":notamodule:deepmodule"));
  }

  @Test
  public void testGetModuleWithDirectory() throws Exception {
    writeToSettingsFile(TestFile.GET_MODULE_WITH_DIRECTORY);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals(ImmutableList.of(":", ":app", ":libs", ":libs:mylibrary", ":olibs", ":olibs:mylibrary", ":notamodule:deepmodule"),
                 settingsModel.modulePaths());

    File rootDir = GradleUtil.getBaseDirPath(myProject);
    assertEquals(":", settingsModel.moduleWithDirectory(rootDir));
    assertEquals(":app", settingsModel.moduleWithDirectory(new File(rootDir, "app")));
    assertEquals(":libs", settingsModel.moduleWithDirectory(new File(rootDir, "libs")));
    assertEquals(":libs:mylibrary", settingsModel.moduleWithDirectory(new File(rootDir, "xyz/mylibrary")));
    assertEquals(":olibs", settingsModel.moduleWithDirectory(new File(rootDir, "otherlibs")));
    assertEquals(":olibs:mylibrary", settingsModel.moduleWithDirectory(new File(rootDir, "otherlibs/mylibrary")));
    assertEquals(":notamodule:deepmodule", settingsModel.moduleWithDirectory(new File(rootDir, "notamodule/deepmodule")));
  }

  @Test
  public void testGetBuildFile() throws Exception {
    writeToSettingsFile(TestFile.GET_BUILD_FILE);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals(ImmutableList.of(":", ":app", ":lib", ":olib"), settingsModel.modulePaths());

    File rootDir = GradleUtil.getBaseDirPath(myProject);
    assertEquals(new File(rootDir, "build.gradle" + (isGroovy()?"":".kts")), settingsModel.buildFile(""));
    assertEquals(new File(rootDir, "app/build.gradle"), settingsModel.buildFile("app"));
    assertEquals(new File(rootDir, "lib/test.gradle" + (isGroovy()?"":".kts")), settingsModel.buildFile(":lib"));
    assertEquals(new File(rootDir, "otherlibs/xyz/other.gradle" + (isGroovy()?"":".kts")), settingsModel.buildFile(":olib"));
  }

  @Test
  public void testGetParentModule() throws Exception {
    writeToSettingsFile(TestFile.GET_PARENT_MODULE);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals(ImmutableList.of(":", ":app", ":libs", ":libs:mylibrary", ":olibs", ":olibs:mylibrary", ":notamodule:deepmodule"),
                 settingsModel.modulePaths());

    assertEquals(":", settingsModel.parentModule("app"));
    assertEquals(":", settingsModel.parentModule(":libs"));
    assertEquals(":libs", settingsModel.parentModule("libs:mylibrary"));
    assertEquals(":", settingsModel.parentModule("olibs"));
    assertEquals(":olibs", settingsModel.parentModule(":olibs:mylibrary"));
    assertEquals(":", settingsModel.parentModule(":notamodule:deepmodule"));
  }

  @Test
  public void testSetProjectDir() throws Exception {
    writeToSettingsFile(TestFile.SET_PROJECT_DIR);

    GradleSettingsModel settingsModel = getGradleSettingsModel();

    settingsModel.setModuleDirectory(":app", new File(myProjectBasePath.getPath(), "newAppLocation"));
    applyChanges(settingsModel);

    // Failure currently expected, the writing format and parsing format for this property don't match.
    //File preParseAppLocation = settingsModel.moduleDirectory(":app");
    //assertEquals(new File(settingsModel.getVirtualFile().getParent().getPath(), "newAppLocation"), preParseAppLocation);

    // Re-parsing should change the property into a readable format.
    settingsModel.reparse();
    File appLocation = settingsModel.moduleDirectory(":app");
    assertEquals(new File(settingsModel.getVirtualFile().getParent().getPath(), "newAppLocation"), appLocation);

    verifyFileContents(mySettingsFile, TestFile.SET_PROJECT_DIR_EXPECTED);
  }

  @Test
  public void testSetProjectDirFromExisting() throws Exception {
    writeToSettingsFile(TestFile.SET_PROJECT_DIR_FROM_EXISTING);
    GradleSettingsModel settingsModel = getGradleSettingsModel();

    settingsModel.setModuleDirectory(":lib", new File(myProjectBasePath.getPath(), "libLocation"));
    applyChanges(settingsModel);

    // Failure currently expected, the writing format and parsing format for this property don't match.
    //File preParseAppLocation = settingsModel.moduleDirectory(":app");
    //assertEquals(new File(settingsModel.getVirtualFile().getParent().getPath(), "newAppLocation"), preParseAppLocation);

    // Re-parsing should change the property into a readable format.
    settingsModel.reparse();
    File appLocation = settingsModel.moduleDirectory(":lib");
    assertEquals(new File(settingsModel.getVirtualFile().getParent().getPath(), "libLocation"), appLocation);

    verifyFileContents(mySettingsFile, TestFile.SET_PROJECT_DIR_FROM_EXISTING_EXPECTED);
  }

  @Test
  public void testSetProjectDirNonRelativePath() throws Exception {
    writeToSettingsFile(TestFile.SET_PROJECT_DIR);

    GradleSettingsModel settingsModel = getGradleSettingsModel();

    settingsModel.setModuleDirectory(":app", new File("/cool/app"));
    applyChanges(settingsModel);

    // Failure currently expected, the writing format and parsing format for this property don't match.
    //File preParseAppLocation = settingsModel.moduleDirectory(":app");
    //assertEquals(new File(settingsModel.getVirtualFile().getParent().getPath(), "newAppLocation"), preParseAppLocation);

    // Re-parsing should change the property into a readable format.
    settingsModel.reparse();
    File appLocation = settingsModel.moduleDirectory(":app");
    File expected = new File("/cool/app").getAbsoluteFile();
    assertEquals(expected, appLocation);
    if (System.getProperty("os.name").startsWith("Windows")) {
      verifyFileContents(mySettingsFile, TestFile.SET_PROJECT_DIR_NON_RELATIVE_WINDOWS_EXPECTED);
    }
    else {
      verifyFileContents(mySettingsFile, TestFile.SET_PROJECT_DIR_NON_RELATIVE_EXPECTED);
    }
  }

  @Test
  public void testExistingVariable() throws Exception {
    writeToSettingsFile(TestFile.EXISTING_VARIABLE);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    settingsModel.addModulePath("lib1");
    applyChanges(settingsModel);

    verifyFileContents(mySettingsFile, TestFile.EXISTING_VARIABLE_EXPECTED);
  }

  private void applyChanges(@NotNull final GradleSettingsModel settingsModel) {
    runWriteCommandAction(myProject, () -> settingsModel.applyChanges());
    assertFalse(settingsModel.isModified());
  }

  enum TestFile implements TestFileName {
    INCLUDED_MODULE_PATHS("includedModulePaths"),
    INCLUDED_MODULE_PATHS_WITH_DOT_SEPARATOR("includedModulePathsWithDotSeparator"),
    INCLUDED_MODULE_PATHS_WITH_DOT_SEPARATOR_SETTINGS("includedModulePathsWithDotSeparatorSettings"),
    ADD_AND_RESET_MODULE_PATHS("addAndResetModulePaths"),
    ADD_AND_APPLY_MODULE_PATHS("addAndApplyModulePaths"),
    ADD_AND_APPLY_MODULE_PATHS_EXPECTED("addAndApplyModulePathsExpected"),
    ADD_AND_APPLY_ALL_MODULE_PATHS("addAndApplyAllModulePaths"),
    ADD_AND_APPLY_ALL_MODULE_PATHS_EXPECTED("addAndApplyAllModulePathsExpected"),
    REMOVE_AND_RESET_MODULE_PATHS("removeAndResetModulePaths"),
    REMOVE_AND_APPLY_MODULE_PATHS("removeAndApplyModulePaths"),
    REMOVE_AND_APPLY_MODULE_PATHS_EXPECTED("removeAndApplyModulePathsExpected"),
    REMOVE_AND_APPLY_ALL_MODULE_PATHS("removeAndApplyAllModulePaths"),
    REMOVE_AND_APPLY_ALL_MODULE_PATHS_EXPECTED("removeAndApplyAllModulePathsExpected"),
    REPLACE_AND_RESET_MODULE_PATHS("replaceAndResetModulePaths"),
    REPLACE_AND_APPLY_MODULE_PATHS("replaceAndApplyModulePaths"),
    REPLACE_AND_APPLY_MODULE_PATHS_EXPECTED("replaceAndApplyModulePathsExpected"),
    GET_MODULE_DIRECTORY("getModuleDirectory"),
    GET_MODULE_WITH_DIRECTORY("getModuleWithDirectory"),
    GET_BUILD_FILE("getBuildFile"),
    GET_PARENT_MODULE("getParentModule"),
    EXISTING_VARIABLE("existingVariable"),
    EXISTING_VARIABLE_EXPECTED("existingVariableExpected"),
    SET_PROJECT_DIR("setProjectDir"),
    SET_PROJECT_DIR_EXPECTED("setProjectDirExpected"),
    SET_PROJECT_DIR_FROM_EXISTING("setProjectDirFromExisting"),
    SET_PROJECT_DIR_FROM_EXISTING_EXPECTED("setProjectDirFromExistingExpected"),
    SET_PROJECT_DIR_NON_RELATIVE_EXPECTED("setProjectDirNonRelativeExpected"),
    SET_PROJECT_DIR_NON_RELATIVE_WINDOWS_EXPECTED("setProjectDirNonRelativeWindowsExpected"),
    ;
    @NotNull private @SystemDependent String path;
    TestFile(@NotNull @SystemDependent String path) {
      this.path = path;
    }

    @NotNull
    @Override
    public File toFile(@NotNull @SystemDependent String basePath, @NotNull String extension) {
      return TestFileName.super.toFile(basePath + "/gradleSettingsModel/" + path, extension);
    }
  }
}
