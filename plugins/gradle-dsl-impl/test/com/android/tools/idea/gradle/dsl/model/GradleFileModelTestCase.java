/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_BUILD_GRADLE_KTS;
import static com.android.SdkConstants.FN_GRADLE_PROPERTIES;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE_KTS;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.BOOLEAN_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.INTEGER_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.LIST_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.MAP_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.OBJECT_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.LIST;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.MAP;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.NONE;
import static com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel.PasswordType;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;
import static org.junit.runners.Parameterized.Parameter;
import static org.junit.runners.Parameterized.Parameters;
import com.android.tools.idea.gradle.dsl.TestFileName;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.PluginModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.FlavorTypeModel.TypeNameValueElement;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.api.util.TypeReference;
import com.google.common.collect.ImmutableMap;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.util.io.PathKt;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@Ignore // Needs to be ignored so bazel doesn't try to run this class as a test and fail with "No tests found".
@RunWith(Parameterized.class)
public abstract class GradleFileModelTestCase extends HeavyPlatformTestCase {
  public static final ProjectSystemId GRADLE_SYSTEM_ID = new ProjectSystemId("GRADLE");
  protected static final String SUB_MODULE_NAME = "gradleModelTest";
  @NotNull private static final String GROOVY_LANGUAGE = "Groovy";
  @NotNull private static final String KOTLIN_LANGUAGE = "Kotlin";

  protected String myTestDataPath;

  @Parameter
  public String myTestDataExtension;
  @Parameter(1)
  public String myLanguageName;

  protected Module mySubModule;

  protected VirtualFile mySettingsFile;
  protected VirtualFile myBuildFile;
  protected VirtualFile myPropertiesFile;
  protected VirtualFile mySubModuleBuildFile;
  protected VirtualFile mySubModulePropertiesFile;

  protected VirtualFile myProjectBasePath;

  @NotNull
  @Contract(pure = true)
  @Parameters(name = "{1}")
  public static Collection languageExtensions() {
    return Arrays.asList(new Object[][]{
      {".gradle", GROOVY_LANGUAGE}
      ,
      {".gradle.kts", KOTLIN_LANGUAGE}
    });
  }

  protected boolean isGroovy() {
    return myLanguageName.equals(GROOVY_LANGUAGE);
  }

  /**
   * @param name the name of an extra property
   *
   * @return the String that corresponds to looking up the extra property {@code name} in the language of this test case
   */
  protected String extraName(String name) {
    return extraName(name, null);
  }

  /**
   * @param name the name of an extra property
   * @param container the (dotted) name of a container holding the extra property
   *
   * @return the String that corresponds to looking up the extra property designated by the arguments in the language of this test case
   */
  protected String extraName(String name, String container) {
    if (myLanguageName.equals(GROOVY_LANGUAGE)) {
      return name;
    }
    else {
      assumeTrue("Language is neither Groovy nor Kotlin", myLanguageName.equals(KOTLIN_LANGUAGE));
      String containerPrefix;
      if (container == null) {
        containerPrefix = "";
      }
      else {
        containerPrefix = container + ".";
      }
      return containerPrefix + "extra[\"" + name + "\"]";
    }
  }

  protected static <R, E extends Exception> void runWriteAction(@NotNull ThrowableComputable<R, E> runnable) throws E {
    ApplicationManager.getApplication().runWriteAction(runnable);
  }

  private static void saveFileUnderWrite(@NotNull VirtualFile file, @NotNull String text) throws IOException {
    runWriteAction(() -> {
      VfsUtil.saveText(file, text);
      return null;
    });
  }

  @Before
  public void before() throws Exception {

    Path basePath = ProjectKt.getStateStore(myProject).getProjectBasePath();
    Files.createDirectories(basePath);
    LocalFileSystem fs = LocalFileSystem.getInstance();
    myProjectBasePath = fs.refreshAndFindFileByNioFile(basePath);
    assertTrue(myProjectBasePath.isDirectory());

    runWriteAction((ThrowableComputable<Void, Exception>)() -> {
      mySettingsFile = myProjectBasePath.createChildData(this, getSettingsFileName());
      assertTrue(mySettingsFile.isWritable());

      Path moduleDirPath = myModule.getModuleNioFile().getParent();
      Files.createDirectories(moduleDirPath);
      VirtualFile moduleVirtualDir = fs.refreshAndFindFileByNioFile(moduleDirPath);
      myBuildFile = moduleVirtualDir.createChildData(this, getBuildFileName());
      assertTrue(myBuildFile.isWritable());
      myPropertiesFile = moduleVirtualDir.createChildData(this, FN_GRADLE_PROPERTIES);
      assertTrue(myPropertiesFile.isWritable());

      Path subModuleNioDir = mySubModule.getModuleNioFile().getParent();
      Files.createDirectories(subModuleNioDir);
      VirtualFile subModuleDirPath = fs.refreshAndFindFileByNioFile(subModuleNioDir);
      assertTrue(subModuleDirPath.isDirectory());
      mySubModuleBuildFile = subModuleDirPath.createChildData(this, getBuildFileName());
      assertTrue(mySubModuleBuildFile.isWritable());
      mySubModulePropertiesFile = subModuleDirPath.createChildData(this, FN_GRADLE_PROPERTIES);
      assertTrue(mySubModulePropertiesFile.isWritable());
      // Setup the project and the module as a Gradle project system so that their build files could be found.
      ExternalSystemModulePropertyManager
        .getInstance(myModule)
        .setExternalOptions(
          GRADLE_SYSTEM_ID,
          new ModuleData(":", GRADLE_SYSTEM_ID, StdModuleTypes.JAVA.getId(), myProjectBasePath.getName(),
                         myProjectBasePath.getPath(), myProjectBasePath.getPath()),
          new ProjectData(GRADLE_SYSTEM_ID, myProject.getName(), myProject.getBasePath(), myProject.getBasePath()));

      return null;
    });

    myTestDataPath = getTestDataPath() + "/parser";
  }

  private String getTestDataPath() {
    return PathManagerEx.findFileUnderCommunityHome("plugins/gradle-dsl-impl/testData").getPath();
  }


  @NotNull
  private String getSettingsFileName() {
    return (isGroovy()) ? FN_SETTINGS_GRADLE : FN_SETTINGS_GRADLE_KTS;
  }

  @NotNull
  private String getBuildFileName() {
    return (isGroovy()) ? FN_BUILD_GRADLE : FN_BUILD_GRADLE_KTS;
  }

  @Override
  @NotNull
  protected Module createMainModule() {
    Module mainModule = createModule(myProject.getName());
    mySubModule = createSubModule(SUB_MODULE_NAME);
    return mainModule;
  }

  private @NotNull Module createSubModule(@NotNull String name) {
    Path moduleFile = ProjectKt.getStateStore(myProject).getProjectBasePath().resolve(name).resolve(name + ModuleFileType.DOT_DEFAULT_EXTENSION);
    return WriteAction.compute(() -> {
      return ModuleManager.getInstance(myProject).newModule(moduleFile, getModuleType().getId());
    });
  }

  protected void prepareAndInjectInformationForTest(@NotNull TestFileName testFileName, @NotNull VirtualFile destination)
    throws IOException {
    final File testFile = testFileName.toFile(myTestDataPath, myTestDataExtension);
    assumeTrue(testFile.exists());
    VirtualFile virtualTestFile = VfsUtil.findFileByIoFile(testFile, true);

    saveFileUnderWrite(destination, VfsUtilCore.loadText(virtualTestFile));
    injectTestInformation(destination);
  }

  protected void writeToSettingsFile(@NotNull String text) throws IOException {
    saveFileUnderWrite(mySettingsFile, text);
  }

  protected void writeToSettingsFile(@NotNull TestFileName fileName) throws IOException {
    prepareAndInjectInformationForTest(fileName, mySettingsFile);
  }

  protected void writeToBuildFile(@NotNull String text) throws IOException {
    saveFileUnderWrite(myBuildFile, text);
  }

  protected void writeToBuildFile(@NotNull TestFileName fileName) throws IOException {
    prepareAndInjectInformationForTest(fileName, myBuildFile);
  }

  protected String getContents(@NotNull TestFileName fileName) throws IOException {
    final File testFile = fileName.toFile(myTestDataPath, myTestDataExtension);
    assumeTrue(testFile.exists());
    return FileUtil.loadFile(testFile);
  }

  protected String getSubModuleSettingsText(String name) {
    return isGroovy() ? ("include ':" + name + "'\n") : ("include(\":" + name + "\")\n");
  }

  protected String getSubModuleSettingsText() {
    return getSubModuleSettingsText(SUB_MODULE_NAME);
  }

  protected Module writeToNewSubModule(@NotNull String name, @NotNull TestFileName fileName, @NotNull String propertiesFileText)
    throws IOException {
    return writeToNewSubModule(name, getContents(fileName), propertiesFileText);
  }

  protected Module writeToNewSubModule(@NotNull String name, @NotNull String buildFileText, @NotNull String propertiesFileText)
    throws IOException {
    Module newModule = createSubModule(name);

    Path newModuleDirPath = newModule.getModuleNioFile().getParent();
    LocalFileSystem fs = LocalFileSystem.getInstance();
    fs.refreshAndFindFileByNioFile(PathKt.write(newModuleDirPath.resolve(getBuildFileName()), buildFileText));
    fs.refreshAndFindFileByNioFile(PathKt.write(newModuleDirPath.resolve(FN_GRADLE_PROPERTIES), propertiesFileText));
    return newModule;
  }

  protected String writeToNewProjectFile(@NotNull String newFileBasename, @NotNull TestFileName testFileName) throws IOException {
    String newFileName = newFileBasename + myTestDataExtension;
    runWriteAction(() -> {
      VirtualFile newFile = myProjectBasePath.createChildData(this, newFileName);
      prepareAndInjectInformationForTest(testFileName, newFile);
      return null;
    });
    return newFileName;
  }

  protected String writeToNewSubModuleFile(@NotNull String newFileBasename, @NotNull TestFileName testFileName) throws IOException {
    String newFileName = newFileBasename + myTestDataExtension;
    runWriteAction(() -> {
      VirtualFile newFile = mySubModuleBuildFile.getParent().createChildData(this, newFileName);
      prepareAndInjectInformationForTest(testFileName, newFile);
      return null;
    });
    return newFileName;
  }

  @NotNull
  protected String loadBuildFile() throws IOException {
    return VfsUtilCore.loadText(myBuildFile);
  }

  protected void writeToPropertiesFile(@NotNull String text) throws IOException {
    saveFileUnderWrite(myPropertiesFile, text);
  }

  protected void writeToSubModuleBuildFile(@NotNull String text) throws IOException {
    saveFileUnderWrite(mySubModuleBuildFile, text);
  }

  protected void writeToSubModuleBuildFile(@NotNull TestFileName fileName) throws IOException {
    prepareAndInjectInformationForTest(fileName, mySubModuleBuildFile);
  }

  private static void injectTestInformation(@NotNull VirtualFile file) throws IOException {
    String content = VfsUtilCore.loadText(file);
    content = content.replace("<SUB_MODULE_NAME>", SUB_MODULE_NAME);
    saveFileUnderWrite(file, content);
  }

  protected void writeToSubModulePropertiesFile(@NotNull String text) throws IOException {
    saveFileUnderWrite(mySubModulePropertiesFile, text);
  }

  @NotNull
  protected GradleSettingsModel getGradleSettingsModel() {
    ProjectBuildModel projectBuildModel = ProjectBuildModel.get(myProject);
    GradleSettingsModel settingsModel = projectBuildModel.getProjectSettingsModel();
    assertNotNull(settingsModel);
    return settingsModel;
  }

  @NotNull
  protected GradleBuildModel getGradleBuildModel() {
    ProjectBuildModel projectBuildModel = ProjectBuildModel.get(myProject);
    GradleBuildModel buildModel = projectBuildModel.getModuleBuildModel(myModule);
    assertNotNull(buildModel);
    return buildModel;
  }

  @NotNull
  protected GradleBuildModel getSubModuleGradleBuildModel() {
    ProjectBuildModel projectBuildModel = ProjectBuildModel.get(myProject);
    GradleBuildModel buildModel = projectBuildModel.getModuleBuildModel(mySubModule);
    assertNotNull(buildModel);
    return buildModel;
  }

  protected void applyChanges(@NotNull final GradleBuildModel buildModel) {
    WriteCommandAction.runWriteCommandAction(myProject, buildModel::applyChanges);
    assertFalse(buildModel.isModified());
  }

  protected void applyChanges(@NotNull final ProjectBuildModel buildModel) {
    WriteCommandAction.runWriteCommandAction(myProject, buildModel::applyChanges);
  }

  protected void applyChangesAndReparse(@NotNull final GradleBuildModel buildModel) {
    applyChanges(buildModel);
    buildModel.reparse();
  }

  protected void verifyFileContents(@NotNull VirtualFile file, @NotNull String contents) throws IOException {
    assertSameLines(contents.replaceAll("[ \\t]+", "").trim(), VfsUtilCore.loadText(file).replaceAll("[ \\t]+", "").trim());
  }

  protected void verifyFileContents(@NotNull VirtualFile file, @NotNull TestFileName expected) throws IOException {
    verifyFileContents(file, FileUtil.loadFile(expected.toFile(myTestDataPath, myTestDataExtension)));
  }

  protected void applyChangesAndReparse(@NotNull final ProjectBuildModel buildModel) {
    applyChanges(buildModel);
    buildModel.reparse();
  }

  protected void removeListValue(@NotNull GradlePropertyModel model, @NotNull Object valueToRemove) {
    assertEquals(LIST, model.getValueType());
    GradlePropertyModel itemModel = model.getListValue(valueToRemove);
    assertNotNull(itemModel);
    itemModel.delete();
  }

  protected void replaceListValue(@NotNull GradlePropertyModel model, @NotNull Object valueToRemove, @NotNull Object valueToAdd) {
    GradlePropertyModel itemModel = model.getListValue(valueToRemove);
    assertNotNull(itemModel);
    itemModel.setValue(valueToAdd);
  }

  protected void verifyPropertyModel(@NotNull GradlePropertyModel propertyModel,
                                     @NotNull String propertyName,
                                     @NotNull String propertyText) {
    verifyPropertyModel(propertyModel, propertyName, propertyText, FileUtil.toSystemIndependentName(myBuildFile.getPath()));
  }

  protected void verifyPropertyModel(@NotNull GradlePropertyModel propertyModel,
                                     @NotNull String propertyName,
                                     @NotNull String propertyText,
                                     @NotNull String propertyFilePath) {
    assertNotNull(propertyModel.getPsiElement());
    assertEquals(propertyText, propertyModel.getValue(STRING_TYPE));
    assertEquals(propertyFilePath, propertyModel.getGradleFile().getPath());
    assertEquals(propertyName, propertyModel.getFullyQualifiedName());
  }

  public static void assertEquals(@NotNull String message, @Nullable String expected, @NotNull GradlePropertyModel actual) {
    assertEquals(message, expected, actual.getValue(STRING_TYPE));
  }

  public static void assertEquals(@NotNull String message, @Nullable Boolean expected, @NotNull GradlePropertyModel actual) {
    assertEquals(message, expected, actual.getValue(BOOLEAN_TYPE));
  }

  public static void assertEquals(@NotNull String message, @Nullable Integer expected, @NotNull GradlePropertyModel actual) {
    assertEquals(message, expected, actual.getValue(INTEGER_TYPE));
  }

  public static void assertEquals(@NotNull String message, @NotNull List<Object> expected, @Nullable GradlePropertyModel propertyModel) {
    verifyListProperty(message, propertyModel, expected);
  }

  public static <T> void assertEquals(@NotNull String message,
                                      @NotNull Map<String, T> expected,
                                      @Nullable GradlePropertyModel propertyModel) {
    verifyMapProperty(message, propertyModel, new HashMap<>(expected));
  }

  public static void verifyFlavorType(@NotNull String message,
                                      @NotNull List<List<Object>> expected,
                                      @Nullable List<? extends TypeNameValueElement> elements) {
    assertEquals(message, expected.size(), elements.size());
    for (int i = 0; i < expected.size(); i++) {
      List<Object> list = expected.get(i);
      TypeNameValueElement element = elements.get(i);
      assertEquals(message, list, element.getModel());
    }
  }

  public static void assertMissingProperty(@NotNull GradlePropertyModel model) {
    assertEquals(NONE, model.getValueType());
  }

  public static void assertMissingProperty(@NotNull String message, @NotNull GradlePropertyModel model) {
    assertEquals(message, NONE, model.getValueType());
  }

  public static <T> void checkForValidPsiElement(@NotNull T object, @NotNull Class<? extends GradleDslBlockModel> clazz) {
    assertThat(object).isInstanceOf(clazz);
    GradleDslBlockModel model = clazz.cast(object);
    assertTrue(model.hasValidPsiElement());
  }

  public static <T> void checkForInValidPsiElement(@NotNull T object, @NotNull Class<? extends GradleDslBlockModel> clazz) {
    assertThat(object).isInstanceOf(clazz);
    GradleDslBlockModel model = clazz.cast(object);
    assertFalse(model.hasValidPsiElement());
  }

  public static <T> boolean hasPsiElement(@NotNull T object) {
    assertThat(object).isInstanceOf(GradleDslBlockModel.class);
    GradleDslBlockModel model = (GradleDslBlockModel)object;
    return model.hasValidPsiElement();
  }

  public static void assertEquals(@NotNull GradlePropertyModel model, @NotNull GradlePropertyModel other) {
    assertTrue(model + " and " + other + " are not equal", areModelsEqual(model, other));
  }

  public static boolean areModelsEqual(@NotNull GradlePropertyModel model, @NotNull GradlePropertyModel other) {
    Object value = model.getValue(OBJECT_TYPE);
    Object otherValue = other.getValue(OBJECT_TYPE);

    if (!Objects.equals(value, otherValue)) {
      return false;
    }

    return model.getValueType().equals(other.getValueType()) &&
           model.getPropertyType().equals(other.getPropertyType()) &&
           model.getGradleFile().equals(other.getGradleFile()) &&
           model.getFullyQualifiedName().equals(other.getFullyQualifiedName());
  }

  public static <T> void verifyPasswordModel(@NotNull PasswordPropertyModel model, T value, PasswordType passwordType) {
    assertEquals(value, model.getValue(OBJECT_TYPE));
    assertEquals(passwordType, model.getType());
  }

  public static <T> void verifyPropertyModel(GradlePropertyModel model, TypeReference<T> type, T value,
                                             ValueType valueType, PropertyType propertyType, int dependencies) {
    assertEquals(valueType, model.getValueType());
    assertEquals(value, model.getValue(type));
    assertEquals(propertyType, model.getPropertyType());
    assertEquals(dependencies, model.getDependencies().size());
  }

  public static void verifyPropertyModel(@NotNull String message, @NotNull GradlePropertyModel model, @NotNull Object expected) {
    verifyPropertyModel(message, model, expected, true);
  }

  public static void verifyPropertyModel(@NotNull String message,
                                         @NotNull GradlePropertyModel model,
                                         @NotNull Object expected,
                                         boolean resolve) {
    switch (model.getValueType()) {
      case INTEGER:
        assertEquals(message, expected, model.getValue(INTEGER_TYPE));
        break;
      case STRING:
        assertEquals(message, expected, model.getValue(STRING_TYPE));
        break;
      case BOOLEAN:
        assertEquals(message, expected, model.getValue(BOOLEAN_TYPE));
        break;
      case REFERENCE:
        if (resolve) {
          GradlePropertyModel resultModel = model.resolve().getResultModel();
          if (resultModel != model) {
            verifyPropertyModel(message, model.resolve().getResultModel(), expected);
            break;
          }
        }
        assertEquals(message, expected, model.getValue(STRING_TYPE));
        break;
      case UNKNOWN:
        assertEquals(message, expected, model.getValue(STRING_TYPE));
        break;
      default:
        fail("Type for model: " + model + " was unexpected, " + model.getValueType());
    }
  }

  public static void verifyListProperty(@Nullable GradlePropertyModel model,
                                        @NotNull List<Object> expectedValues) {
    verifyListProperty(model, expectedValues, true);
  }

  public static void verifyListProperty(@Nullable GradlePropertyModel model,
                                        @NotNull String name,
                                        @NotNull List<Object> expectedValues) {
    verifyListProperty("verifyListProperty", model, expectedValues);
    assertEquals(name, model.getFullyQualifiedName());
  }

  public static void verifyListProperty(@Nullable GradlePropertyModel model,
                                        @NotNull List<Object> expectedValues,
                                        boolean resolveItem) {
    verifyListProperty("verifyListProperty", model, expectedValues, resolveItem);
  }

  public static void verifyListProperty(@NotNull String message,
                                        @Nullable GradlePropertyModel model,
                                        @NotNull List<Object> expectedValues) {
    verifyListProperty(message, model, expectedValues, true);
  }

  // This method is not suitable for lists or maps in lists, these must be verified manually.
  public static void verifyListProperty(GradlePropertyModel model,
                                        List<Object> expectedValues,
                                        PropertyType propertyType,
                                        int dependencies) {
    verifyListProperty("verifyListProperty", model, expectedValues);
    assertEquals(propertyType, model.getPropertyType());
    assertEquals(dependencies, model.getDependencies().size());
  }

  public static void verifyListProperty(GradlePropertyModel model,
                                        List<Object> expectedValues,
                                        PropertyType propertyType,
                                        int dependencies,
                                        String name) {
    verifyListProperty("verifyListProperty", model, expectedValues);
    assertEquals(propertyType, model.getPropertyType());
    assertEquals(dependencies, model.getDependencies().size());
    assertEquals(name, model.getName());
  }

  public static void verifyListProperty(GradlePropertyModel model,
                                        List<Object> expectedValues,
                                        PropertyType propertyType,
                                        int dependencies,
                                        String name,
                                        String fullName) {
    verifyListProperty(model, expectedValues, propertyType, dependencies, name);
    assertEquals(fullName, model.getFullyQualifiedName());
  }

  public static void verifyListProperty(@NotNull String message,
                                        @Nullable GradlePropertyModel model,
                                        @NotNull List<Object> expectedValues,
                                        boolean resolveItems) {
    assertNotNull(message, model);
    assertEquals(message, LIST, model.getValueType());
    List<GradlePropertyModel> actualValues = model.getValue(LIST_TYPE);
    assertNotNull(message, actualValues);
    assertEquals(message, expectedValues.size(), actualValues.size());
    for (int i = 0; i < actualValues.size(); i++) {
      GradlePropertyModel tempModel = actualValues.get(i);
      verifyPropertyModel(message, tempModel, expectedValues.get(i), resolveItems);
    }
  }

  public static void verifyEmptyMapProperty(@Nullable GradlePropertyModel model) {
    verifyEmptyMapProperty("verifyEmptyMapProperty", model);
  }

  public static void verifyEmptyMapProperty(@NotNull String message, @Nullable GradlePropertyModel model) {
    verifyMapProperty(message, model, ImmutableMap.of());
  }

  public static void verifyMapProperty(@Nullable GradlePropertyModel model,
                                       @NotNull Map<String, Object> expectedValues,
                                       @NotNull PropertyType type,
                                       int dependencies,
                                       @NotNull String name) {
    verifyMapProperty(model, expectedValues);
    assertEquals(type, model.getPropertyType());
    assertEquals(dependencies, model.getDependencies().size());
    assertEquals(name, model.getName());
  }

  public static void verifyMapProperty(@Nullable GradlePropertyModel model,
                                       @NotNull Map<String, Object> expectedValues,
                                       @NotNull String name,
                                       @NotNull String fullName) {
    verifyMapProperty(model, expectedValues);
    assertEquals(name, model.getName());
    assertEquals(fullName, model.getFullyQualifiedName());
  }

  public static void verifyMapProperty(@Nullable GradlePropertyModel model,
                                       @NotNull Map<String, Object> expectedValues) {
    verifyMapProperty("verifyMapProperty", model, expectedValues);
  }

  public static void verifyMapProperty(@NotNull String message,
                                       @Nullable GradlePropertyModel model,
                                       @NotNull Map<String, Object> expectedValues) {
    assertNotNull(message, model);
    assertEquals(message, MAP, model.getValueType());
    Map<String, GradlePropertyModel> actualValues = model.getValue(MAP_TYPE);
    assertNotNull(message, actualValues);
    assertEquals(message, expectedValues.entrySet().size(), actualValues.entrySet().size());
    for (String key : actualValues.keySet()) {
      GradlePropertyModel tempModel = actualValues.get(key);
      Object expectedValue = expectedValues.get(key);
      assertNotNull(message, expectedValue);
      verifyPropertyModel(message, tempModel, expectedValue);
    }
  }

  public static <T> void verifyPropertyModel(GradlePropertyModel model,
                                             TypeReference<T> type,
                                             T value,
                                             ValueType valueType,
                                             PropertyType propertyType,
                                             int dependencies,
                                             String name) {
    verifyPropertyModel(model, type, value, valueType, propertyType, dependencies);
    assertEquals(name, model.getName());
  }

  public static <T> void verifyPropertyModel(GradlePropertyModel model,
                                             TypeReference<T> type,
                                             T value,
                                             ValueType valueType,
                                             PropertyType propertyType,
                                             int dependencies,
                                             String name,
                                             String fullName) {
    verifyPropertyModel(model, type, value, valueType, propertyType, dependencies);
    assertEquals(name, model.getName());
    assertEquals(fullName, model.getFullyQualifiedName());
  }

  public static void verifyFilePathsAreEqual(@NotNull VirtualFile expected, @NotNull VirtualFile actual) {
    assertEquals(FileUtil.toSystemIndependentName(expected.getPath()), actual.getPath());
  }

  public static void verifyPlugins(@NotNull List<String> names, @NotNull List<PluginModel> models) {
    List<String> actualNames = PluginModel.extractNames(models);
    assertSameElements(names, actualNames);
  }
}
