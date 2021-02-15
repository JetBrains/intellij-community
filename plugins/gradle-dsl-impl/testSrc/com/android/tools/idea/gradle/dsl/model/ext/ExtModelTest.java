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
package com.android.tools.idea.gradle.dsl.model.ext;

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_BUILD_SCRIPT_EXT_USAGE;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_DEF_USED_IN_DEF_RESOLVED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_DEPENDENCY_EXT_USAGE;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_EXT_FLAT_AND_BLOCK;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_EXT_FLAT_AND_BLOCK_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_FLAT_DEF_VARIABLES_ARE_RESOLVED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_LIST_REFERENCE_IN_LIST_PROPERTY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_MAP_REFERENCE_IN_MAP_PROPERTY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_MULTIPLE_DEF_DECLARATIONS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_MULTIPLE_EXT_BLOCKS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_MULTIPLE_EXT_BLOCKS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_NESTED_DEF_VARIABLES_ARE_RESOLVED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_PARSING_LIST_OF_PROPERTIES;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_PARSING_SIMPLE_PROPERTY_IN_EXT_BLOCK;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_PARSING_SIMPLE_PROPERTY_PER_LINE;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_PROPERTY_NAMES;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_RESOLVE_EXT_PROPERTY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_RESOLVE_MULTI_LEVEL_EXT_PROPERTY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_RESOLVE_MULTI_LEVEL_EXT_PROPERTY_WITH_HISTORY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_RESOLVE_MULTI_MODULE_EXT_PROPERTY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_RESOLVE_MULTI_MODULE_EXT_PROPERTY_FROM_PROPERTIES_WITH_HISTORY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_RESOLVE_MULTI_MODULE_EXT_PROPERTY_FROM_PROPERTIES_WITH_HISTORY_SUB;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_RESOLVE_MULTI_MODULE_EXT_PROPERTY_SUB;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_RESOLVE_MULTI_MODULE_EXT_PROPERTY_WITH_HISTORY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_RESOLVE_MULTI_MODULE_EXT_PROPERTY_WITH_HISTORY_SUB;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_RESOLVE_QUALIFIED_EXT_PROPERTY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_RESOLVE_QUALIFIED_VARIABLE_IN_STRING_LITERAL;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_RESOLVE_VARIABLES_IN_STRING_LITERAL;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_RESOLVE_VARIABLE_IN_LIST_PROPERTY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_RESOLVE_VARIABLE_IN_MAINMODULE_BUILD_FILE;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_RESOLVE_VARIABLE_IN_MAINMODULE_BUILD_FILE_SUB;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_RESOLVE_VARIABLE_IN_MAINMODULE_PROPERTIES_FILE;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_RESOLVE_VARIABLE_IN_MAINMODULE_PROPERTIES_FILE_SUB;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_RESOLVE_VARIABLE_IN_MAP_PROPERTY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_RESOLVE_VARIABLE_IN_SUBMODULE_BUILD_FILE;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_RESOLVE_VARIABLE_IN_SUBMODULE_BUILD_FILE_SUB;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_RESOLVE_VARIABLE_IN_SUBMODULE_PROPERTIES_FILE;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_RESOLVE_VARIABLE_IN_SUBMODULE_PROPERTIES_FILE_SUB;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_STRING_REFERENCE_IN_LIST_PROPERTY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.EXT_MODEL_STRING_REFERENCE_IN_MAP_PROPERTY;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.BOOLEAN_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.INTEGER_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.BOOLEAN;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.INTEGER;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.NONE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.REFERENCE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.DERIVED;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.FAKE;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.PROPERTIES_FILE;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.ProductFlavorModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import org.junit.Test;

/**
 * Tests for {@link ExtModel}.
 */
public class ExtModelTest extends GradleFileModelTestCase {
  @Test
  public void testParsingSimplePropertyPerLine() throws IOException {
    writeToBuildFile(EXT_MODEL_PARSING_SIMPLE_PROPERTY_PER_LINE);

    ExtModel extModel = getGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("COMPILE_SDK_VERSION"), INTEGER_TYPE, 21, INTEGER, REGULAR, 0);
    verifyPropertyModel(extModel.findProperty("srcDirName"), STRING_TYPE, "src/java", STRING, REGULAR, 0);
  }

  @Test
  public void testParsingSimplePropertyInExtBlock() throws IOException {
    writeToBuildFile(EXT_MODEL_PARSING_SIMPLE_PROPERTY_IN_EXT_BLOCK);

    ExtModel extModel = getGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("COMPILE_SDK_VERSION"), INTEGER_TYPE, 21, INTEGER, REGULAR, 0);
    verifyPropertyModel(extModel.findProperty("srcDirName"), STRING_TYPE, "src/java", STRING, REGULAR, 0);
  }

  @Test
  public void testParsingListOfProperties() throws IOException {
    writeToBuildFile(EXT_MODEL_PARSING_LIST_OF_PROPERTIES);


    ExtModel extModel = getGradleBuildModel().ext();
    GradlePropertyModel model = extModel.findProperty("libraries").toMap().get("guava");
    verifyPropertyModel(model, STRING_TYPE, "com.google.guava:guava:19.0-rc1", STRING, DERIVED, 0, "guava");
  }

  @Test
  public void testResolveExtProperty() throws IOException {
    writeToBuildFile(EXT_MODEL_RESOLVE_EXT_PROPERTY);

    ExtModel extModel = getGradleBuildModel().ext();
    GradlePropertyModel model = extModel.findProperty("COMPILE_SDK_VERSION");
    verifyPropertyModel(model, INTEGER_TYPE, 21, INTEGER, REGULAR, 0, "COMPILE_SDK_VERSION");

    AndroidModel androidModel = getGradleBuildModel().android();
    assertNotNull(androidModel);
    verifyPropertyModel(androidModel.compileSdkVersion(), INTEGER_TYPE, 21, INTEGER, REGULAR, 1);
  }

  @Test
  public void testResolveQualifiedExtProperty() throws IOException {
    writeToBuildFile(EXT_MODEL_RESOLVE_QUALIFIED_EXT_PROPERTY);

    ExtModel extModel = getGradleBuildModel().ext();
    GradlePropertyModel model = extModel.findProperty("constants").toMap().get("COMPILE_SDK_VERSION");
    verifyPropertyModel(model, INTEGER_TYPE, 21, INTEGER, DERIVED, 0, "COMPILE_SDK_VERSION");

    AndroidModel androidModel = getGradleBuildModel().android();
    assertNotNull(androidModel);
    verifyPropertyModel(androidModel.compileSdkVersion(), INTEGER_TYPE, 21, INTEGER, REGULAR, 1);
  }

  @Test
  public void testResolveMultiLevelExtProperty() throws IOException {
    writeToBuildFile(EXT_MODEL_RESOLVE_MULTI_LEVEL_EXT_PROPERTY);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = getGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("SDK_VERSION"), INTEGER_TYPE, 21, INTEGER, REGULAR, 0);
    verifyPropertyModel(extModel.findProperty("COMPILE_SDK_VERSION"), STRING_TYPE, extraName("SDK_VERSION"), REFERENCE, REGULAR, 1);
    verifyPropertyModel(extModel.findProperty("COMPILE_SDK_VERSION").resolve(), INTEGER_TYPE, 21, INTEGER, REGULAR, 1);

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    assertEquals("compileSdkVersion", "21", androidModel.compileSdkVersion());

    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("targetSdkVersion", 21, defaultConfig.targetSdkVersion());
  }

  @Test
  public void testResolveMultiModuleExtProperty() throws IOException {
    writeToSettingsFile(getSubModuleSettingsText());
    writeToBuildFile(EXT_MODEL_RESOLVE_MULTI_MODULE_EXT_PROPERTY);
    writeToSubModuleBuildFile(EXT_MODEL_RESOLVE_MULTI_MODULE_EXT_PROPERTY_SUB);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = buildModel.ext();
    verifyPropertyModel(extModel.findProperty("SDK_VERSION"), INTEGER_TYPE, 21, INTEGER, REGULAR, 0);


    GradleBuildModel subModuleBuildModel = getSubModuleGradleBuildModel();
    ExtModel subModuleExtModel = getSubModuleGradleBuildModel().ext();
    assertMissingProperty(subModuleExtModel.findProperty("SDK_VERSION"));

    AndroidModel androidModel = subModuleBuildModel.android();
    assertNotNull(androidModel);
    assertEquals("compileSdkVersion", "21", androidModel.compileSdkVersion()); // SDK_VERSION resolved from the main module.
  }

  @Test
  public void testResolveVariablesInStringLiteral() throws IOException {
    writeToBuildFile(EXT_MODEL_RESOLVE_VARIABLES_IN_STRING_LITERAL);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = getGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("ANDROID"), STRING_TYPE, "android", STRING, REGULAR, 0);
    verifyPropertyModel(extModel.findProperty("SDK_VERSION"), INTEGER_TYPE, 23, INTEGER, REGULAR, 0);


    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    assertEquals("compileSdkVersion", "android-23", androidModel.compileSdkVersion());

    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("targetSdkVersion", "android-23", defaultConfig.targetSdkVersion());
  }

  @Test
  public void testResolveQualifiedVariableInStringLiteral() throws IOException {
    writeToBuildFile(EXT_MODEL_RESOLVE_QUALIFIED_VARIABLE_IN_STRING_LITERAL);

    GradleBuildModel buildModel = getGradleBuildModel();

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    assertEquals("compileSdkVersion", "23", androidModel.compileSdkVersion());

    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion());
  }

  @Test
  public void testStringReferenceInListProperty() throws IOException {
    writeToBuildFile(EXT_MODEL_STRING_REFERENCE_IN_LIST_PROPERTY);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = getGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("TEST_STRING"), STRING_TYPE, "test", STRING, REGULAR, 0);

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "test"), defaultConfig.proguardFiles());
  }

  @Test
  public void testListReferenceInListProperty() throws IOException {
    writeToBuildFile(EXT_MODEL_LIST_REFERENCE_IN_LIST_PROPERTY);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = getGradleBuildModel().ext();

    verifyListProperty(extModel.findProperty("TEST_STRINGS"), ImmutableList.of("test1", "test2"));

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("proguardFiles", ImmutableList.of("test1", "test2"), defaultConfig.proguardFiles());
  }

  @Test
  public void testResolveVariableInListProperty() throws IOException {
    writeToBuildFile(EXT_MODEL_RESOLVE_VARIABLE_IN_LIST_PROPERTY);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = getGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("TEST_STRING"), STRING_TYPE, "test", STRING, REGULAR, 0);

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "test"), defaultConfig.proguardFiles());
  }

  @Test
  public void testStringReferenceInMapProperty() throws IOException {
    writeToBuildFile(EXT_MODEL_STRING_REFERENCE_IN_MAP_PROPERTY);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = getGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("TEST_STRING"), STRING_TYPE, "test", STRING, REGULAR, 0);

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "test"),
                 defaultConfig.testInstrumentationRunnerArguments());
  }

  @Test
  public void testMapReferenceInMapProperty() throws IOException {
    writeToBuildFile(EXT_MODEL_MAP_REFERENCE_IN_MAP_PROPERTY);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = buildModel.ext();
    assertNotNull(extModel);

    GradlePropertyModel expressionMap = extModel.findProperty("TEST_MAP");
    assertNotNull(expressionMap);
    assertEquals("TEST_MAP", ImmutableMap.of("test1", "value1", "test2", "value2"), expressionMap);

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertNotNull(defaultConfig);
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("test1", "value1", "test2", "value2"),
                 defaultConfig.testInstrumentationRunnerArguments());
  }

  @Test
  public void testResolveVariableInMapProperty() throws IOException {
    writeToBuildFile(EXT_MODEL_RESOLVE_VARIABLE_IN_MAP_PROPERTY);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = getGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("TEST_STRING"), STRING_TYPE, "test", STRING, REGULAR, 0);

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    ProductFlavorModel defaultConfig = androidModel.defaultConfig();
    assertEquals("testInstrumentationRunnerArguments", ImmutableMap.of("size", "medium", "foo", "test"),
                 defaultConfig.testInstrumentationRunnerArguments());
  }

  @Test
  public void testResolveVariableInSubModuleBuildFile() throws IOException {
    String mainModulePropertiesText = "xyz=value_from_main_module_properties_file";
    String subModulePropertiesText = "xyz=value_from_sub_module_properties_file";

    writeToSettingsFile(getSubModuleSettingsText());
    writeToPropertiesFile(mainModulePropertiesText);
    writeToBuildFile(EXT_MODEL_RESOLVE_VARIABLE_IN_SUBMODULE_BUILD_FILE);
    writeToSubModulePropertiesFile(subModulePropertiesText);
    writeToSubModuleBuildFile(EXT_MODEL_RESOLVE_VARIABLE_IN_SUBMODULE_BUILD_FILE_SUB);

    ExtModel extModel = getSubModuleGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("test").resolve(), STRING_TYPE, "value_from_sub_module_build_file", STRING, REGULAR, 1);
  }

  @Test
  public void testResolveVariableInSubModulePropertiesFile() throws IOException {
    String mainModulePropertiesText = "xyz=value_from_main_module_properties_file";
    String subModulePropertiesText = "xyz=value_from_sub_module_properties_file";

    writeToSettingsFile(getSubModuleSettingsText());
    writeToPropertiesFile(mainModulePropertiesText);
    writeToBuildFile(EXT_MODEL_RESOLVE_VARIABLE_IN_SUBMODULE_PROPERTIES_FILE);
    writeToSubModulePropertiesFile(subModulePropertiesText);
    writeToSubModuleBuildFile(EXT_MODEL_RESOLVE_VARIABLE_IN_SUBMODULE_PROPERTIES_FILE_SUB);

    ExtModel extModel = getSubModuleGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("test").resolve(), STRING_TYPE, "value_from_sub_module_properties_file", STRING, REGULAR, 1);
  }

  @Test
  public void testResolveVariableInMainModulePropertiesFile() throws IOException {
    String mainModulePropertiesText = "xyz=value_from_main_module_properties_file";

    writeToSettingsFile(getSubModuleSettingsText());
    writeToPropertiesFile(mainModulePropertiesText);
    writeToBuildFile(EXT_MODEL_RESOLVE_VARIABLE_IN_MAINMODULE_PROPERTIES_FILE);
    writeToSubModuleBuildFile(EXT_MODEL_RESOLVE_VARIABLE_IN_MAINMODULE_PROPERTIES_FILE_SUB);

    ExtModel extModel = getSubModuleGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("test").resolve(), STRING_TYPE, "value_from_main_module_properties_file", STRING, REGULAR, 1);
  }

  @Test
  public void testResolveVariableInMainModuleBuildFile() throws IOException {
    writeToSettingsFile(getSubModuleSettingsText());
    writeToBuildFile(EXT_MODEL_RESOLVE_VARIABLE_IN_MAINMODULE_BUILD_FILE);
    writeToSubModuleBuildFile(EXT_MODEL_RESOLVE_VARIABLE_IN_MAINMODULE_BUILD_FILE_SUB);

    ExtModel extModel = getSubModuleGradleBuildModel().ext();
    verifyPropertyModel(extModel.findProperty("test").resolve(), STRING_TYPE, "value_from_main_module_build_file", STRING, REGULAR, 1);
  }

  @Test
  public void testResolveMultiLevelExtPropertyWithHistory() throws IOException {
    writeToBuildFile(EXT_MODEL_RESOLVE_MULTI_LEVEL_EXT_PROPERTY_WITH_HISTORY);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = buildModel.ext();

    verifyPropertyModel(extModel.findProperty("THIRD").resolve(), INTEGER_TYPE, 123, INTEGER, REGULAR, 1);
    verifyPropertyModel(extModel.findProperty("THIRD"), STRING_TYPE, extraName("SECOND"), REFERENCE, REGULAR, 1);
    verifyPropertyModel(extModel.findProperty("SECOND").resolve(), INTEGER_TYPE, 123, INTEGER, REGULAR, 1);
    verifyPropertyModel(extModel.findProperty("SECOND"), STRING_TYPE, extraName("FIRST"), REFERENCE, REGULAR, 1);
    verifyPropertyModel(extModel.findProperty("FIRST").resolve(), INTEGER_TYPE, 123, INTEGER, REGULAR, 0);
    verifyPropertyModel(extModel.findProperty("FIRST"), INTEGER_TYPE, 123, INTEGER, REGULAR, 0);
  }

  @Test
  public void testResolveMultiModuleExtPropertyWithHistory() throws IOException {
    writeToSettingsFile(getSubModuleSettingsText());
    writeToBuildFile(EXT_MODEL_RESOLVE_MULTI_MODULE_EXT_PROPERTY_WITH_HISTORY);
    writeToSubModuleBuildFile(EXT_MODEL_RESOLVE_MULTI_MODULE_EXT_PROPERTY_WITH_HISTORY_SUB);

    GradleBuildModel buildModel = getSubModuleGradleBuildModel();
    ExtModel extModel = buildModel.ext();

    GradlePropertyModel second = extModel.findProperty("SECOND");
    verifyPropertyModel(second.resolve(), INTEGER_TYPE, 123, INTEGER, REGULAR, 1);
    verifyPropertyModel(second, STRING_TYPE, extraName("FIRST", "rootProject"), REFERENCE, REGULAR, 1);
    GradlePropertyModel first = second.getDependencies().get(0);
    verifyPropertyModel(first.resolve(), INTEGER_TYPE, 123, INTEGER, REGULAR, 0);
    verifyPropertyModel(first, INTEGER_TYPE, 123, INTEGER, REGULAR, 0);
  }

  @Test
  public void testResolveMultiModuleExtPropertyFromPropertiesFileWithHistory() throws IOException {
    String mainModulePropertiesText = "first=value_from_gradle_properties";

    writeToSettingsFile(getSubModuleSettingsText());
    writeToPropertiesFile(mainModulePropertiesText);
    writeToBuildFile(EXT_MODEL_RESOLVE_MULTI_MODULE_EXT_PROPERTY_FROM_PROPERTIES_WITH_HISTORY);
    writeToSubModuleBuildFile(EXT_MODEL_RESOLVE_MULTI_MODULE_EXT_PROPERTY_FROM_PROPERTIES_WITH_HISTORY_SUB);

    GradleBuildModel buildModel = getSubModuleGradleBuildModel();
    ExtModel extModel = buildModel.ext();

    GradlePropertyModel third = extModel.findProperty("third");
    verifyPropertyModel(third.resolve(), STRING_TYPE, "value_from_gradle_properties", STRING, REGULAR, 1);
    verifyPropertyModel(third, STRING_TYPE, extraName("second", "rootProject"), REFERENCE, REGULAR, 1);
    GradlePropertyModel second = third.getDependencies().get(0);
    verifyPropertyModel(second.resolve(), STRING_TYPE, "value_from_gradle_properties", STRING, REGULAR, 1);
    verifyPropertyModel(second, STRING_TYPE, "first", REFERENCE, REGULAR, 1);
    GradlePropertyModel first = second.getDependencies().get(0);
    verifyPropertyModel(first.resolve(), STRING_TYPE, "value_from_gradle_properties", STRING, PROPERTIES_FILE, 0);
    verifyPropertyModel(first, STRING_TYPE, "value_from_gradle_properties", STRING, PROPERTIES_FILE, 0);
  }

  @Test
  public void testFlatDefVariablesAreResolved() throws IOException {
    writeToBuildFile(EXT_MODEL_FLAT_DEF_VARIABLES_ARE_RESOLVED);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = buildModel.ext();

    verifyPropertyModel(extModel.findProperty("first").resolve(), STRING_TYPE, "Hello WORLD", STRING, REGULAR, 1);
  }

  @Test
  public void testNestedDefVariablesAreResolved() throws IOException {
    writeToBuildFile(EXT_MODEL_NESTED_DEF_VARIABLES_ARE_RESOLVED);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = buildModel.ext();

    verifyPropertyModel(extModel.findProperty("second").resolve(), STRING_TYPE, "Welcome to bar world!", STRING, REGULAR, 2);
  }

  @Test
  public void testMultipleDefDeclarations() throws IOException {
    isIrrelevantForKotlinScript("no multiple declaration syntax in KotlinScript");
    writeToBuildFile(EXT_MODEL_MULTIPLE_DEF_DECLARATIONS);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = buildModel.ext();

    verifyPropertyModel(extModel.findProperty("prop").resolve(), STRING_TYPE, "hello world", STRING, REGULAR, 1);
    verifyPropertyModel(extModel.findProperty("prop2").resolve(), STRING_TYPE, "hello world", STRING, REGULAR, 1);
  }

  @Test
  public void testDefUsedInDefResolved() throws IOException {
    writeToBuildFile(EXT_MODEL_DEF_USED_IN_DEF_RESOLVED);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel extModel = buildModel.ext();

    verifyPropertyModel(extModel.findProperty("greeting").resolve(), STRING_TYPE, "Hello, penguins are cool!", STRING, REGULAR, 1);
  }

  @Test
  public void testDependencyExtUsage() throws IOException {
    writeToBuildFile(EXT_MODEL_DEPENDENCY_EXT_USAGE);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel buildScriptDeps = buildModel.buildscript().dependencies();

    assertSize(1, buildScriptDeps.artifacts());
    ArtifactDependencyModel dependencyModel = buildScriptDeps.artifacts().get(0);
    verifyPropertyModel(dependencyModel.version(), STRING_TYPE, "boo", STRING, FAKE, 1);

    // Check outta Ext can't see the inner one.
    ExtModel extModel = buildModel.ext();
    verifyPropertyModel(extModel.findProperty("goodbye"), STRING_TYPE, "hello", REFERENCE, REGULAR, 0);
    verifyPropertyModel(extModel.findProperty("goodday").resolve(), STRING_TYPE, "boo", STRING, REGULAR, 1);
  }

  @Test
  public void testBuildScriptExtUsage() throws IOException {
    writeToBuildFile(EXT_MODEL_BUILD_SCRIPT_EXT_USAGE);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel buildScriptDeps = buildModel.buildscript().dependencies();

    assertSize(1, buildScriptDeps.artifacts());
    ArtifactDependencyModel dependencyModel = buildScriptDeps.artifacts().get(0);
    verifyPropertyModel(dependencyModel.version(), STRING_TYPE, "boo", STRING, FAKE, 1);
  }

  @Test
  public void testMultipleExtBlocks() throws IOException {
    writeToBuildFile(EXT_MODEL_MULTIPLE_EXT_BLOCKS);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel ext = buildModel.ext();

    ext.findProperty("ext.newProp").setValue(true);

    applyChangesAndReparse(buildModel);

    GradlePropertyModel propertyModel = ext.findProperty("newProp");
    verifyPropertyModel(propertyModel, BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0);
    GradlePropertyModel oldModel = ext.findProperty("var1");
    verifyPropertyModel(oldModel, STRING_TYPE, "1.5", STRING, REGULAR, 0);

    verifyFileContents(myBuildFile, EXT_MODEL_MULTIPLE_EXT_BLOCKS_EXPECTED);
  }

  @Test
  public void testExtFlatAndBlock() throws IOException {
    writeToBuildFile(EXT_MODEL_EXT_FLAT_AND_BLOCK);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel ext = buildModel.ext();

    ext.findProperty("newProp").setValue(true);

    applyChangesAndReparse(buildModel);

    GradlePropertyModel helloModel = ext.findProperty("hello");
    verifyPropertyModel(helloModel, INTEGER_TYPE, 10, INTEGER, REGULAR, 0);
    GradlePropertyModel booModel = ext.findProperty("boo");
    verifyPropertyModel(booModel, STRING_TYPE, "10", STRING, REGULAR, 0);
    GradlePropertyModel newModel = ext.findProperty("newProp");

    verifyPropertyModel(newModel, BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0);

    verifyFileContents(myBuildFile, EXT_MODEL_EXT_FLAT_AND_BLOCK_EXPECTED);
  }

  @Test
  public void testPropertyNames() throws IOException {
    writeToBuildFile(EXT_MODEL_PROPERTY_NAMES);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExtModel ext = buildModel.ext();

    verifyPropertyModel(ext.findProperty("isDebuggable"), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0);
    verifyPropertyModel(ext.findProperty("foo"), STRING_TYPE, "isDebuggable", REFERENCE, REGULAR, 1);
    verifyPropertyModel(ext.findProperty("foo").resolve(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 1);
    verifyPropertyModel(ext.findProperty("debuggable"), BOOLEAN_TYPE, null, NONE, REGULAR, 0);
  }
}
