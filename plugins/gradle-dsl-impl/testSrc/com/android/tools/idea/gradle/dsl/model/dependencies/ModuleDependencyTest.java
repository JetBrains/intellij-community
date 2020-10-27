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
package com.android.tools.idea.gradle.dsl.model.dependencies;

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.MODULE_DEPENDENCY_ADD_CLOSURE_TO_DEPENDENCY;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.MODULE_DEPENDENCY_INSERTION_ORDER;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.MODULE_DEPENDENCY_INSERTION_ORDER_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.MODULE_DEPENDENCY_INSERT_PSI_ELEMENT_AFTER_FILE_BLOCK_COMMENT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.MODULE_DEPENDENCY_INSERT_PSI_ELEMENT_AFTER_FILE_BLOCK_COMMENT_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.MODULE_DEPENDENCY_MULTI_TYPE_APPLICATION_STATEMENT_DOES_NOT_THROW_EXCEPTION;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.MODULE_DEPENDENCY_PARSING_WITH_COMPACT_NOTATION;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.MODULE_DEPENDENCY_PARSING_WITH_DEPENDENCY_ON_ROOT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.MODULE_DEPENDENCY_PARSING_WITH_MAP_NOTATION;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.MODULE_DEPENDENCY_REMOVE_WHEN_MULTIPLE;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.MODULE_DEPENDENCY_RESET;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.MODULE_DEPENDENCY_SET_CONFIGURATION_WHEN_MULTIPLE;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.MODULE_DEPENDENCY_SET_CONFIGURATION_WHEN_SINGLE;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.MODULE_DEPENDENCY_SET_NAMES_ON_ITEMS_IN_EXPRESSION_LIST;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.MODULE_DEPENDENCY_SET_NAME_ON_COMPACT_NOTATION;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.MODULE_DEPENDENCY_SET_NAME_ON_MAP_NOTATION_WITHOUT_CONFIGURATION;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.MODULE_DEPENDENCY_SET_NAME_ON_MAP_NOTATION_WITH_CONFIGURATION;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.MODULE_DEPENDENCY_SET_NAME_WITH_PATH_HAVING_SAME_SEGMENT_NAMES;
import static com.google.common.truth.Truth.assertThat;
import static org.jetbrains.kotlin.lexer.KtTokens.BLOCK_COMMENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ML_COMMENT;
import static org.junit.Assume.assumeTrue;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModelImpl;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslClosure;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.intellij.psi.PsiElement;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtFile;
import org.junit.Test;

/**
 * Tests for {@link DependenciesModelImpl} and {@link ModuleDependencyModelImpl}.
 */
public class ModuleDependencyTest extends GradleFileModelTestCase {
  @Test
  public void testParsingWithCompactNotation() throws IOException {
    writeToBuildFile(MODULE_DEPENDENCY_PARSING_WITH_COMPACT_NOTATION);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(5);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":javalib1";
    assertMatches(expected, dependencies.get(0));

    ExpectedModuleDependency expected1 = new ExpectedModuleDependency();
    expected1.configurationName = "test";
    expected1.path = ":test1";
    assertMatches(expected1, dependencies.get(1));
    ExpectedModuleDependency expected2 = new ExpectedModuleDependency();
    expected2.configurationName = "test";
    expected2.path = ":test2";
    assertMatches(expected2, dependencies.get(2));
    ExpectedModuleDependency expected3 = new ExpectedModuleDependency();
    expected3.configurationName = "androidTestImplementation";
    expected3.path = ":test3";
    assertMatches(expected3, dependencies.get(3));
    ExpectedModuleDependency expected4 = new ExpectedModuleDependency();
    expected4.configurationName = "androidTestImplementation";
    expected4.path = ":test4";
    assertMatches(expected4, dependencies.get(4));
  }

  @Test
  public void testParsingWithDependencyOnRoot() throws IOException {
    writeToBuildFile(MODULE_DEPENDENCY_PARSING_WITH_DEPENDENCY_ON_ROOT);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);

    ModuleDependencyModel actual = dependencies.get(0);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":";
    assertMatches(expected, actual);

    assertEquals("", actual.name());
  }

  @Test
  public void testParsingWithMapNotation() throws IOException {
    writeToBuildFile(MODULE_DEPENDENCY_PARSING_WITH_MAP_NOTATION);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(3);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":androidlib1";
    expected.configuration = "flavor1Release";
    assertMatches(expected, dependencies.get(0));

    expected.reset();

    expected.configurationName = "compile";
    expected.path = ":androidlib2";
    expected.configuration = "flavor2Release";
    assertMatches(expected, dependencies.get(1));

    expected.reset();

    expected.configurationName = "runtime";
    expected.path = ":javalib2";
    assertMatches(expected, dependencies.get(2));
  }

  @Test
  public void testSetConfigurationWhenSingle() throws Exception {
    writeToBuildFile(MODULE_DEPENDENCY_SET_CONFIGURATION_WHEN_SINGLE);
    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> modules = buildModel.dependencies().modules();
    assertSize(4, modules);

    assertThat(modules.get(0).configurationName()).isEqualTo("test");
    modules.get(0).setConfigurationName("androidTest");
    assertThat(modules.get(0).configurationName()).isEqualTo("androidTest");

    assertThat(modules.get(1).configurationName()).isEqualTo("compile");
    modules.get(1).setConfigurationName("zapi");
    assertThat(modules.get(1).configurationName()).isEqualTo("zapi");
    modules.get(1).setConfigurationName("api"); // Try twice.
    assertThat(modules.get(1).configurationName()).isEqualTo("api");

    assertThat(modules.get(2).configurationName()).isEqualTo("api");
    modules.get(2).setConfigurationName("zompile");
    assertThat(modules.get(2).configurationName()).isEqualTo("zompile");
    modules.get(2).setConfigurationName("compile"); // Try twice
    assertThat(modules.get(2).configurationName()).isEqualTo("compile");

    assertThat(modules.get(3).configurationName()).isEqualTo("testCompile");
    modules.get(3).setConfigurationName("testImplementation");
    assertThat(modules.get(3).configurationName()).isEqualTo("testImplementation");

    applyChangesAndReparse(buildModel);

    modules = buildModel.dependencies().modules();
    assertSize(4, modules);

    assertThat(modules.get(0).configurationName()).isEqualTo("androidTest");
    assertThat(modules.get(0).path().toString()).isEqualTo(":abc");

    assertThat(modules.get(1).configurationName()).isEqualTo("api");
    assertThat(modules.get(1).path().toString()).isEqualTo(":xyz");

    assertThat(modules.get(2).configurationName()).isEqualTo("compile");
    assertThat(modules.get(2).path().toString()).isEqualTo(":klm");

    assertThat(modules.get(3).configurationName()).isEqualTo("testImplementation");
    assertThat(modules.get(3).path().toString()).isEqualTo(":");
  }

  @Test
  public void testSetConfigurationWhenMultiple() throws Exception {
    writeToBuildFile(MODULE_DEPENDENCY_SET_CONFIGURATION_WHEN_MULTIPLE);
    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> modules = buildModel.dependencies().modules();
    assertSize(5, modules);
    assertThat(modules.get(0).configurationName()).isEqualTo("testCompile");
    assertThat(modules.get(0).path().toString()).isEqualTo(":abc");

    assertThat(modules.get(1).configurationName()).isEqualTo("testCompile");
    assertThat(modules.get(1).path().toString()).isEqualTo(":xyz");

    assertThat(modules.get(2).configurationName()).isEqualTo("compile");
    assertThat(modules.get(2).path().toString()).isEqualTo(":klm");

    assertThat(modules.get(3).configurationName()).isEqualTo("compile");
    assertThat(modules.get(3).path().toString()).isEqualTo(":");

    assertThat(modules.get(4).configurationName()).isEqualTo("compile");
    assertThat(modules.get(4).path().toString()).isEqualTo(":pqr");

    {
      modules.get(0).setConfigurationName("androidTest");
      List<ModuleDependencyModel> updatedModules = buildModel.dependencies().modules();
      assertSize(5, updatedModules);
      assertThat(updatedModules.get(0).configurationName()).isEqualTo("androidTest");
      assertThat(updatedModules.get(0).path().toString()).isEqualTo(":abc");

      assertThat(updatedModules.get(1).configurationName()).isEqualTo("testCompile");
      assertThat(updatedModules.get(1).path().toString()).isEqualTo(":xyz");
    }

    {
      // Rename both elements of the same group and rename some of them twice.
      modules.get(2).setConfigurationName("zapi");
      modules.get(2).setConfigurationName("api");
      modules.get(4).setConfigurationName("zimplementation");
      modules.get(4).setConfigurationName("implementation");
      List<ModuleDependencyModel> updatedModules = buildModel.dependencies().modules();
      assertSize(5, updatedModules);
      // Note: The renamed element becomes the first in the group.
      assertThat(updatedModules.get(2).configurationName()).isEqualTo("api");
      assertThat(updatedModules.get(2).path().toString()).isEqualTo(":klm");

      assertThat(updatedModules.get(3).configurationName()).isEqualTo("implementation");
      assertThat(updatedModules.get(3).path().toString()).isEqualTo(":pqr");
      assertThat(updatedModules.get(3).configuration().toString()).isEqualTo("config");

      assertThat(updatedModules.get(4).configurationName()).isEqualTo("compile");
      assertThat(updatedModules.get(4).path().toString()).isEqualTo(":");
    }

    applyChangesAndReparse(buildModel);

    modules = buildModel.dependencies().modules();
    assertSize(5, modules);

    assertThat(modules.get(0).configurationName()).isEqualTo("androidTest");
    assertThat(modules.get(0).path().toString()).isEqualTo(":abc");

    assertThat(modules.get(1).configurationName()).isEqualTo("testCompile");
    assertThat(modules.get(1).path().toString()).isEqualTo(":xyz");

    assertThat(modules.get(2).configurationName()).isEqualTo("api");
    assertThat(modules.get(2).path().toString()).isEqualTo(":klm");

    assertThat(modules.get(3).configurationName()).isEqualTo("implementation");
    assertThat(modules.get(3).path().toString()).isEqualTo(":pqr");
    assertThat(modules.get(3).configuration().toString()).isEqualTo("config");

    assertThat(modules.get(4).configurationName()).isEqualTo("compile");
    assertThat(modules.get(4).path().toString()).isEqualTo(":");
  }

  @Test
  public void testSetNameOnCompactNotation() throws IOException {
    writeToBuildFile(MODULE_DEPENDENCY_SET_NAME_ON_COMPACT_NOTATION);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    ModuleDependencyModel dependency = dependencies.get(0);
    dependency.setName("newName");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);
    dependency = dependencies.get(0);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":newName";
    assertMatches(expected, dependency);
  }

  @Test
  public void testSetNameOnMapNotationWithConfiguration() throws IOException {
    writeToBuildFile(MODULE_DEPENDENCY_SET_NAME_ON_MAP_NOTATION_WITH_CONFIGURATION);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    ModuleDependencyModel dependency = dependencies.get(0);
    dependency.setName("newName");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);
    dependency = dependencies.get(0);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":newName";
    expected.configuration = "flavor1Release";
    assertMatches(expected, dependency);
  }

  @Test
  public void testSetNamesOnItemsInExpressionList() throws IOException {
    writeToBuildFile(MODULE_DEPENDENCY_SET_NAMES_ON_ITEMS_IN_EXPRESSION_LIST);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    ModuleDependencyModel dependency1 = dependencies.get(0);
    dependency1.setName("newName");

    ModuleDependencyModel dependency2 = dependencies.get(1);
    dependency2.setName("newName2");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(2);
    dependency1 = dependencies.get(0);
    dependency2 = dependencies.get(1);

    ExpectedModuleDependency expected1 = new ExpectedModuleDependency();
    expected1.configurationName = "compile";
    expected1.path = ":newName";
    expected1.configuration = "flavor1Release";

    ExpectedModuleDependency expected2 = new ExpectedModuleDependency();
    expected2.configurationName = "compile";
    expected2.path = ":newName2";
    assertMatches(expected1, dependency1);
    assertMatches(expected2, dependency2);
  }

  @Test
  public void testSetNameOnMapNotationWithoutConfiguration() throws IOException {
    writeToBuildFile(MODULE_DEPENDENCY_SET_NAME_ON_MAP_NOTATION_WITHOUT_CONFIGURATION);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    ModuleDependencyModel dependency = dependencies.get(0);
    dependency.setName("newName");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    dependency = dependencies.get(0);

    dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":newName";
    assertMatches(expected, dependency);
  }

  @Test
  public void testSetNameWithPathHavingSameSegmentNames() throws IOException {
    writeToBuildFile(MODULE_DEPENDENCY_SET_NAME_WITH_PATH_HAVING_SAME_SEGMENT_NAMES);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    ModuleDependencyModel dependency = dependencies.get(0);
    dependency.setName("helloWorld");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);
    dependency = dependencies.get(0);

    ModuleDependencyModel actual = dependencies.get(0);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":name:helloWorld";
    assertMatches(expected, actual);

    assertEquals("helloWorld", actual.name());
  }

  @Test
  public void testRemoveWhenMultiple() throws IOException {
    writeToBuildFile(MODULE_DEPENDENCY_REMOVE_WHEN_MULTIPLE);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(2);

    ModuleDependencyModel dependency1 = dependencies.get(0);
    assertThat(dependency1.path().toString()).isEqualTo(":androidlib1");

    ModuleDependencyModel dependency2 = dependencies.get(1);
    assertThat(dependency2.path().toString()).isEqualTo(":other");

    buildModel.dependencies().remove(dependency2);

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);

    ModuleDependencyModel actual = dependencies.get(0);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.configuration = "flavor1Release";
    expected.path = ":androidlib1";
    assertMatches(expected, actual);

    assertEquals("androidlib1", actual.name());
  }

  @Test
  public void testReset() throws IOException {
    writeToBuildFile(MODULE_DEPENDENCY_RESET);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    ModuleDependencyModel dependency = dependencies.get(0);
    dependency.setName("newName");

    assertTrue(buildModel.isModified());

    buildModel.resetState();

    assertFalse(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);
    dependency = dependencies.get(0);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":javalib1";
    assertMatches(expected, dependency);
  }

  // Test for b/68188327
  @Test
  public void testMultiTypeApplicationStatementDoesNotThrowException() throws IOException {
    writeToBuildFile(MODULE_DEPENDENCY_MULTI_TYPE_APPLICATION_STATEMENT_DOES_NOT_THROW_EXCEPTION);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();

    // Note: this is not correct behaviour, this tests that no exception occurs.
    // TODO(b/69115152): fix the implementation of this
    assertThat(dependencies).hasSize(0);
  }

  @Test
  public void testInsertPsiElementAfterFileBlockComment() throws Exception {
    writeToBuildFile(MODULE_DEPENDENCY_INSERT_PSI_ELEMENT_AFTER_FILE_BLOCK_COMMENT);

    GradleBuildModel buildModel = getGradleBuildModel();

    DependencyModel dependency = buildModel.dependencies().all().get(0);
    buildModel.dependencies().remove(dependency);

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    buildModel.dependencies().addModule("compile", ":module1");
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, MODULE_DEPENDENCY_INSERT_PSI_ELEMENT_AFTER_FILE_BLOCK_COMMENT_EXPECTED);

    PsiElement psiFile = ((GradleBuildModelImpl)buildModel).getDslFile().getPsiElement();
    if (myLanguageName.equals("Groovy")) {
      assertTrue(psiFile.getFirstChild().getNode().getElementType() == ML_COMMENT);
    }
    else if(myLanguageName.equals("Kotlin")){
      assertTrue(((KtFile)psiFile).getScript().getBlockExpression().getFirstChild().getNode().getElementType() == BLOCK_COMMENT);
    }
  }

  @Test
  public void testAddClosureBlockToDependency() throws IOException {
    assumeTrue("uses KotlinScript-specific Dsl construct to make and populate closure", isKotlinScript());
    writeToBuildFile(MODULE_DEPENDENCY_ADD_CLOSURE_TO_DEPENDENCY);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<DependencyModel> dependencies = buildModel.dependencies().all();
    List<ModuleDependencyModel> modules = buildModel.dependencies().modules();

    assertEquals(modules.size(), 1);
    assertThat(modules.get(0).configurationName()).isEqualTo("testImplementation");
    assertThat(modules.get(0).path().toString()).isEqualTo(":");

    DependencyModelImpl testDependency = ((DependencyModelImpl)dependencies.get(0));

    GradleDslClosure excludeBlock = new GradleDslClosure(testDependency.getDslElement(), null, testDependency.getDslElement().getNameElement());
    GradleDslMethodCall exclude = new GradleDslMethodCall(excludeBlock, GradleNameElement.fake("exclude"), "exclude");
    GradleDslLiteral moduleValue = new GradleDslLiteral(exclude, GradleNameElement.fake("module"));
    moduleValue.setValue("module1");
    exclude.addNewArgument(moduleValue);
    excludeBlock.addNewElementAt(0, exclude);
    testDependency.getDslElement().setNewClosureElement(excludeBlock);


    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    modules = buildModel.dependencies().modules();
    assertEquals(modules.size(), 1);
    assertThat(modules.get(0).configurationName()).isEqualTo("testImplementation");
    assertNotNull(((ModuleDependencyModelImpl)modules.get(0)).getDslElement().getClosureElement());
  }

  @Test
  public void testInsertionOrder() throws IOException {
    writeToBuildFile(MODULE_DEPENDENCY_INSERTION_ORDER);

    GradleBuildModel buildModel = getGradleBuildModel();

    buildModel.dependencies().addModule("api", ":module1");
    buildModel.dependencies().addModule("testImplementation", ":module2");
    buildModel.dependencies().addModule("androidTestApi", ":module3");
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    verifyFileContents(myBuildFile, MODULE_DEPENDENCY_INSERTION_ORDER_EXPECTED);
  }

  private static void assertMatches(@NotNull ExpectedModuleDependency expected, @NotNull ModuleDependencyModel actual) {
    assertEquals("configurationName", expected.configurationName, actual.configurationName());
    assertEquals("path", expected.path, actual.path().forceString());
    assertEquals("configuration", expected.configuration, actual.configuration().toString());
  }
}
