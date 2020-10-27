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

import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.ANDROID_TEST_COMPILE;
import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.CLASSPATH;
import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.COMPILE;
import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.IMPLEMENTATION;
import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.RUNTIME;
import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.TEST_COMPILE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.INTEGER_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.INTEGER;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.REFERENCE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.iStr;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.DERIVED;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.FAKE;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.idea.gradle.dsl.TestFileName;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyConfigurationModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ExcludedDependencyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.psi.PsiElement;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemDependent;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.junit.Test;

/**
 * Tests for {@link DependenciesModelImpl} and {@link ArtifactDependencyModelImpl}.
 */
public class ArtifactDependencyTest extends GradleFileModelTestCase {
  @Test
  public void testParsingWithCompactNotationAndConfigurationClosure_parens() throws IOException {
    doTestParsingConfigurationVersion(TestFile.CONFIGURE_CLOSURE_PARENS);
  }

  @Test
  public void testParsingWithCompactNotationAndConfigurationClosure_noParens() throws IOException {
    isIrrelevantForKotlinScript("No paren-less dependency configuration form in KotlinScript");
    doTestParsingConfigurationVersion(TestFile.CONFIGURE_CLOSURE_NO_PARENS);
  }

  @Test
  public void testParsingWithCompactNotationAndConfigurationClosure_withinParens() throws IOException {
    assumeTrue("KotlinScript handling of closure as internal argument" , !isKotlinScript()); // TODO(b/155168291)
    doTestParsingConfigurationVersion(TestFile.CONFIGURE_CLOSURE_WITH_PARENS);
  }

  private void doTestParsingConfigurationVersion(@NotNull TestFileName fileName) throws IOException {
    writeToBuildFile(fileName);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ArtifactDependencyModel dependency = dependencies.get(0);
    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "hibernate", "org.hibernate", "3.1");
    expected.assertMatches(dependency);

    verifyDependencyConfiguration(dependency.configuration());
  }

  private static void verifyDependencyConfiguration(@Nullable DependencyConfigurationModel configuration) {
    assertNotNull(configuration);

    assertEquals(Boolean.TRUE, configuration.force().toBoolean());
    assertEquals(Boolean.FALSE, configuration.transitive().toBoolean());

    List<ExcludedDependencyModel> excludedDependencies = configuration.excludes();
    assertThat(excludedDependencies).hasSize(3);

    ExcludedDependencyModel first = excludedDependencies.get(0);
    assertNull(first.group().toString());
    assertEquals("cglib", first.module().toString());

    ExcludedDependencyModel second = excludedDependencies.get(1);
    assertEquals("org.jmock", second.group().toString());
    assertNull(second.module().toString());

    ExcludedDependencyModel third = excludedDependencies.get(2);
    assertEquals("org.unwanted", third.group().toString());
    assertEquals("iAmBuggy", third.module().toString());
  }

  @Test
  public void testSetVersionOnDependencyWithCompactNotationAndConfigurationClosure_parens() throws IOException {
    doTestSetVersionWithConfigurationClosure(TestFile.CONFIGURE_CLOSURE_PARENS);
  }

  @Test
  public void testSetVersionOnDependencyWithCompactNotationAndConfigurationClosure_noParens() throws IOException {
    isIrrelevantForKotlinScript("No paren-less dependency configuration form in KotlinScript");
    doTestSetVersionWithConfigurationClosure(TestFile.CONFIGURE_CLOSURE_NO_PARENS);
  }

  @Test
  public void testSetVersionOnDependencyWithCompactNotationAndConfigurationClosure_withinParens() throws IOException {
    assumeTrue("KotlinScript handling of closure as internal argument" , !isKotlinScript()); // TODO(b/155168291)
    doTestSetVersionWithConfigurationClosure(TestFile.CONFIGURE_CLOSURE_WITH_PARENS);
  }

  private void doTestSetVersionWithConfigurationClosure(@NotNull TestFileName fileName) throws IOException {
    writeToBuildFile(fileName);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ArtifactDependencyModel hibernate = dependencies.get(0);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "hibernate", "org.hibernate", "3.1");
    expected.assertMatches(hibernate);
    verifyDependencyConfiguration(hibernate.configuration());

    hibernate.version().setValue("3.0");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    hibernate = dependencies.get(0);

    expected = new ExpectedArtifactDependency(COMPILE, "hibernate", "org.hibernate", "3.0");
    expected.assertMatches(hibernate);
    verifyDependencyConfiguration(hibernate.configuration());
  }

  @Test
  public void testGetOnlyArtifacts() throws IOException {
    writeToBuildFile(TestFile.GET_ONLY_ARTIFACTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    expected.assertMatches(dependencies.get(1));
  }

  @Test
  public void testParsingWithCompactNotation() throws IOException {
    writeToBuildFile(TestFile.PARSING_WITH_COMPACT_NOTATION);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(3);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(RUNTIME, "guava", "com.google.guava", "18.0");
    expected.assertMatches(dependencies.get(1));

    expected = new ExpectedArtifactDependency("test", "service", "org.gradle.test.classifiers", "1.0");
    expected.setClassifier("jdk15");
    expected.setExtension("jar");
    expected.assertMatches(dependencies.get(2));

    // We do not support: test wrapped('something:else:1.0')
  }

  @Test
  public void testParsingWithMapNotation() throws IOException {
    writeToBuildFile(TestFile.PARSING_WITH_MAP_NOTATION);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(RUNTIME, "service", "org.gradle.test.classifiers", "1.0");
    expected.setClassifier("jdk14");
    expected.setExtension("jar");
    expected.assertMatches(dependencies.get(0));
  }

  @Test
  public void testAddDependency() throws IOException {
    writeToBuildFile(TestFile.ADD_DEPENDENCY);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    ArtifactDependencySpecImpl newDependency = new ArtifactDependencySpecImpl("appcompat-v7", "com.android.support", "22.1.1");
    dependenciesModel.addArtifact(COMPILE, newDependency);

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_DEPENDENCY_EXPECTED);

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(RUNTIME, "service", "org.gradle.test.classifiers", "1.0");
    expected.setClassifier("jdk14");
    expected.setExtension("jar");
    expected.assertMatches(dependencies.get(1));

    expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(0));
  }

  @Test
  public void testAddDependencyWithConfigurationClosure() throws IOException {
    writeToBuildFile(TestFile.ADD_DEPENDENCY_WITH_CONFIGURATION_CLOSURE);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    ArtifactDependencySpecImpl newDependency =
      new ArtifactDependencySpecImpl("espresso-contrib", "com.android.support.test.espresso", "2.2.2");
    dependenciesModel.addArtifact(ANDROID_TEST_COMPILE,
                                  newDependency,
                                  ImmutableList.of(new ArtifactDependencySpecImpl("support-v4", "com.android.support", null),
                                                   new ArtifactDependencySpecImpl("support-annotations", "com.android.support", null),
                                                   new ArtifactDependencySpecImpl("recyclerview-v7", "com.android.support", null),
                                                   new ArtifactDependencySpecImpl("design", "com.android.support", null)));

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_DEPENDENCY_WITH_CONFIGURATION_CLOSURE_EXPECTED);

    dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ArtifactDependencyModel jdkDependency = dependencies.get(1);
    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(RUNTIME, "service", "org.gradle.test.classifiers", "1.0");
    expected.setClassifier("jdk14");
    expected.setExtension("jar");
    expected.assertMatches(jdkDependency);
    assertNull(jdkDependency.configuration());

    ArtifactDependencyModel espressoDependency = dependencies.get(0);
    expected = new ExpectedArtifactDependency(ANDROID_TEST_COMPILE, "espresso-contrib", "com.android.support.test.espresso", "2.2.2");
    expected.assertMatches(espressoDependency);

    DependencyConfigurationModel configuration = espressoDependency.configuration();
    assertNotNull(configuration);

    configuration.excludes();

    List<ExcludedDependencyModel> excludedDependencies = configuration.excludes();
    assertThat(excludedDependencies).hasSize(4);

    ExcludedDependencyModel first = excludedDependencies.get(0);
    assertEquals("com.android.support", first.group().toString());
    assertEquals("support-v4", first.module().toString());

    ExcludedDependencyModel second = excludedDependencies.get(1);
    assertEquals("com.android.support", second.group().toString());
    assertEquals("support-annotations", second.module().toString());

    ExcludedDependencyModel third = excludedDependencies.get(2);
    assertEquals("com.android.support", third.group().toString());
    assertEquals("recyclerview-v7", third.module().toString());

    ExcludedDependencyModel fourth = excludedDependencies.get(3);
    assertEquals("com.android.support", fourth.group().toString());
    assertEquals("design", fourth.module().toString());
  }

  @Test
  public void testSetVersionOnDependencyWithCompactNotation() throws IOException {
    writeToBuildFile(TestFile.SET_VERSION_ON_DEPENDENCY_WITH_COMPACT_NOTATION);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();

    ArtifactDependencyModel appCompat = dependencies.get(0);
    appCompat.version().setValue("1.2.3");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.SET_VERSION_ON_DEPENDENCY_WITH_COMPACT_NOTATION_EXPECTED);

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "1.2.3");
    expected.assertMatches(dependencies.get(0));
  }

  @Test
  public void testSetVersionOnDependencyWithMapNotation() throws IOException {
    writeToBuildFile(TestFile.SET_VERSION_ON_DEPENDENCY_WITH_MAP_NOTATION);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();

    ArtifactDependencyModel guice = dependencies.get(0);
    guice.version().setValue("1.2.3");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.SET_VERSION_ON_DEPENDENCY_WITH_MAP_NOTATION_EXPECTED);

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "guice", "com.google.code.guice", "1.2.3");
    expected.assertMatches(dependencies.get(0));
  }

  @Test
  public void testSetDependencyWithCompactNotation() throws IOException {
    writeToBuildFile(TestFile.SET_DEPENDENCY_WITH_COMPACT_NOTATION);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();
    ExtModel ext = buildModel.ext();

    // Create an extra property.
    ext.findProperty("ext.appCom").setValue("28.0.0");

    dependenciesModel.addArtifact("implementation", "androidx.appcompat:appcompat:${appCom}");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.SET_DEPENDENCY_WITH_COMPACT_NOTATION_EXPECTED);

  }

  @Test
  public void testParseDependenciesWithCompactNotationInSingleLine() throws IOException {
    isIrrelevantForKotlinScript("No multiple dependency configuration form in KotlinScript");
    writeToBuildFile(TestFile.PARSE_DEPENDENCIES_WITH_COMPACT_NOTATION_IN_SINGLE_LINE);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(RUNTIME, "spring-core", "org.springframework", "2.5");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(RUNTIME, "spring-aop", "org.springframework", "2.5");
    expected.assertMatches(dependencies.get(1));
  }

  @Test
  public void testParseDependenciesWithCompactNotationInSingleLineWithComments() throws IOException {
    isIrrelevantForKotlinScript("No multiple dependency configuration form in KotlinScript");
    writeToBuildFile(TestFile.PARSE_DEPENDENCIES_WITH_COMPACT_NOTATION_IN_SINGLE_LINE_WITH_COMMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(RUNTIME, "spring-core", "org.springframework", "2.5");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(RUNTIME, "spring-aop", "org.springframework", "2.5");
    expected.assertMatches(dependencies.get(1));
  }

  @Test
  public void testParseDependenciesWithMapNotationUsingSingleConfigurationName() throws IOException {
    isIrrelevantForKotlinScript("No multiple dependency configuration form in KotlinScript");
    writeToBuildFile(TestFile.PARSE_DEPENDENCIES_WITH_MAP_NOTATION_USING_SINGLE_CONFIGURATION_NAME);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(RUNTIME, "spring-core", "org.springframework", "2.5");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(RUNTIME, "spring-aop", "org.springframework", "2.5");
    expected.assertMatches(dependencies.get(1));
  }

  @Test
  public void testParseDependenciesWithMapNotationUsingSingleConfigNoParentheses() throws IOException {
    isIrrelevantForKotlinScript("No multiple dependency configuration form in KotlinScript");
    writeToBuildFile(TestFile.PARSE_DEPENDENCIES_WITH_MAP_NOTATION_USING_SINGLE_CONFIGURATION_NAME_NO_PARENTHESES);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(RUNTIME, "spring-core", "org.springframework", "2.5");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(RUNTIME, "spring-aop", "org.springframework", "2.5");
    expected.assertMatches(dependencies.get(1));
  }

  @Test
  public void testReset() throws IOException {
    writeToBuildFile(TestFile.RESET);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();

    ArtifactDependencyModel guice = dependencies.get(0);
    guice.version().setValue("1.2.3");

    assertTrue(buildModel.isModified());

    buildModel.resetState();

    assertFalse(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.RESET);

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "guice", "com.google.code.guice", "1.0");
    expected.assertMatches(dependencies.get(0));
  }

  @Test
  public void testRemoveDependencyWithCompactNotation() throws IOException {
    writeToBuildFile(TestFile.REMOVE_DEPENDENCY_WITH_COMPACT_NOTATION);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(3);

    ArtifactDependencyModel guava = dependencies.get(1);
    dependenciesModel.remove(guava);

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.REMOVE_DEPENDENCY_WITH_COMPACT_NOTATION_EXPECTED);

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency("test", "service", "org.gradle.test.classifiers", "1.0");
    expected.setClassifier("jdk15");
    expected.setExtension("jar");
    expected.assertMatches(dependencies.get(1));
  }

  @Test
  public void testRemoveDependencyWithCompactNotationAndSingleConfigurationName() throws IOException {
    isIrrelevantForKotlinScript("No multiple dependency configuration form in KotlinScript");
    writeToBuildFile(TestFile.REMOVE_DEPENDENCY_WITH_COMPACT_NOTATION_AND_SINGLE_CONFIGURATION_NAME);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(3);

    ArtifactDependencyModel springAop = dependencies.get(1);
    dependenciesModel.remove(springAop);

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(RUNTIME, "spring-core", "org.springframework", "2.5");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency("test", "service", "org.gradle.test.classifiers", "1.0");
    expected.setClassifier("jdk15");
    expected.setExtension("jar");
    expected.assertMatches(dependencies.get(1));
  }

  @Test
  public void testRemoveDependencyWithMapNotation() throws IOException {
    writeToBuildFile(TestFile.REMOVE_DEPENDENCY_WITH_MAP_NOTATION);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(3);

    ArtifactDependencyModel guava = dependencies.get(1);
    dependenciesModel.remove(guava);

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.REMOVE_DEPENDENCY_WITH_MAP_NOTATION_EXPECTED);

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency("compile", "guice", "com.google.code.guice", "1.0");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(1));
  }

  @Test
  public void testRemoveDependencyWithMapNotationAndSingleConfigurationName() throws IOException {
    isIrrelevantForKotlinScript("No multiple dependency configuration form in KotlinScript");
    writeToBuildFile(TestFile.REMOVE_DEPENDENCY_WITH_MAP_NOTATION_AND_SINGLE_CONFIGURATION_NAME);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(3);

    ArtifactDependencyModel guava = dependencies.get(1);
    dependenciesModel.remove(guava);

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(RUNTIME, "guice", "com.google.code.guice", "1.0");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(RUNTIME, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(1));
  }

  @Test
  public void testRemoveDependencyWhenMultiple() throws IOException {
    isIrrelevantForKotlinScript("No multiple dependency configuration form in KotlinScript");
    writeToBuildFile(TestFile.REMOVE_WHEN_MULTIPLE);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(4);

    ArtifactDependencyModel guiceGuava = dependencies.get(1);
    assertThat(guiceGuava.compactNotation()).isEqualTo("com.google.code.guice:guava:1.0");
    dependenciesModel.remove(guiceGuava);

    ArtifactDependencyModel guava = dependencies.get(2);
    assertThat(guava.compactNotation()).isEqualTo("com.google.guava:guava:18.0");
    dependenciesModel.remove(guava);

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "guice", "com.google.code.guice", "1.0");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(COMPILE, "something", "org.example", "1.0");
    expected.assertMatches(dependencies.get(1));
  }

  @Test
  public void testContains() throws IOException {
    writeToBuildFile(TestFile.CONTAINS);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    ArtifactDependencySpecImpl guavaSpec = new ArtifactDependencySpecImpl("guava", "com.google.guava", "18.0");
    ArtifactDependencySpecImpl guice1Spec = new ArtifactDependencySpecImpl("guice", "com.google.code.guice", "1.0");
    ArtifactDependencySpecImpl guice2Spec = new ArtifactDependencySpecImpl("guice", "com.google.code.guice", "2.0");
    ArtifactDependencySpecImpl appcompat = new ArtifactDependencySpecImpl("appcompat-v7", "com.android.support", "22.1.1");
    ArtifactDependencySpecImpl notAppcompat = new ArtifactDependencySpecImpl("appcompat-v7", "com.example", "22.1.1");

    assertTrue(dependenciesModel.containsArtifact(COMPILE, guavaSpec));
    assertFalse(dependenciesModel.containsArtifact(CLASSPATH, guavaSpec));
    assertTrue(dependenciesModel.containsArtifact(COMPILE, guice1Spec));
    assertFalse(dependenciesModel.containsArtifact(COMPILE, guice2Spec));
    assertTrue(dependenciesModel.containsArtifact(COMPILE, appcompat));
    assertFalse(dependenciesModel.containsArtifact(COMPILE, notAppcompat));
  }

  @Test
  public void testParseCompactNotationWithVariables() throws IOException {
    writeToBuildFile(TestFile.PARSE_COMPACT_NOTATION_WITH_VARIABLES);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    ArtifactDependencyModel appcompatDependencyModel = dependencies.get(0);
    expected.assertMatches(appcompatDependencyModel);
    assertEquals(expected.compactNotation(), appcompatDependencyModel.compactNotation());
    List<GradlePropertyModel> appcompatResolvedVariables = appcompatDependencyModel.completeModel().getDependencies();
    assertEquals(1, appcompatResolvedVariables.size());

    GradlePropertyModel appcompatVariable = appcompatResolvedVariables.get(0);
    verifyPropertyModel(appcompatVariable.resolve(), STRING_TYPE, "com.android.support:appcompat-v7:22.1.1", STRING, REGULAR, 0,
                        "appcompat", "ext.appcompat");

    expected = new ExpectedArtifactDependency(RUNTIME, "guava", "com.google.guava", "18.0");
    ArtifactDependencyModel guavaDependencyModel = dependencies.get(1);
    expected.assertMatches(guavaDependencyModel);
    assertEquals(expected.compactNotation(), guavaDependencyModel.compactNotation());
    List<GradlePropertyModel> guavaResolvedVariables = guavaDependencyModel.completeModel().getDependencies();
    assertEquals(1, guavaResolvedVariables.size());

    GradlePropertyModel guavaVersionVariable = guavaResolvedVariables.get(0);
    verifyPropertyModel(guavaVersionVariable, STRING_TYPE, "18.0", STRING, REGULAR, 0, "guavaVersion", "ext.guavaVersion");
  }

  @Test
  public void testParseCompactNotationWithQuotedIdentifierVariables() throws IOException {
    writeToBuildFile(TestFile.PARSE_COMPACT_NOTATION_WITH_QUOTED_IDENTIFIER_VARIABLES);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(6);

    assertEquals(new ExpectedArtifactDependency(IMPLEMENTATION, "a", "com.google.a", "1.0.0").compactNotation(),
                 dependencies.get(0).compactNotation());
    assertEquals(new ExpectedArtifactDependency(IMPLEMENTATION, "b", "com.google.b", "2.0.0").compactNotation(),
                 dependencies.get(1).compactNotation());
    assertEquals(new ExpectedArtifactDependency(IMPLEMENTATION, "c", "com.google.c", "3.0.0").compactNotation(),
                 dependencies.get(2).compactNotation());
    assertEquals(new ExpectedArtifactDependency(IMPLEMENTATION, "d", "com.google.d", "4.0.0").compactNotation(),
                 dependencies.get(3).compactNotation());
    assertEquals(new ExpectedArtifactDependency(IMPLEMENTATION, "e", "com.google.e", "5.0.0").compactNotation(),
                 dependencies.get(4).compactNotation());
    assertEquals(new ExpectedArtifactDependency(IMPLEMENTATION, "f", "com.google.f", "6.0.0").compactNotation(),
                 dependencies.get(5).compactNotation());
  }

  @Test
  public void testParseMapNotationWithVariables() throws IOException {
    writeToBuildFile(TestFile.PARSE_MAP_NOTATION_WITH_VARIABLES);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    ArtifactDependencyModel guavaDependencyModel = dependencies.get(0);
    assertEquals(expected.compactNotation(), guavaDependencyModel.compactNotation());
    List<GradlePropertyModel> guavaResolvedVariables = guavaDependencyModel.completeModel().getDependencies();
    assertEquals(1, guavaResolvedVariables.size());

    GradlePropertyModel guavaVersionVariable = guavaResolvedVariables.get(0);
    verifyPropertyModel(guavaVersionVariable, STRING_TYPE, "18.0", STRING, REGULAR, 0, "guavaVersion", "ext.guavaVersion");

    // Now test that resolved variables are not reported for group and name properties.
    GradlePropertyModel group = guavaDependencyModel.group();
    verifyPropertyModel(group, STRING_TYPE, "com.google.guava", STRING, DERIVED, 0, "group");

    GradlePropertyModel name = guavaDependencyModel.name();
    verifyPropertyModel(name, STRING_TYPE, "guava", STRING, DERIVED, 0, "name");

    // and thee guavaVersion variable is reported for version property.
    GradlePropertyModel version = guavaDependencyModel.version();
    verifyPropertyModel(version, STRING_TYPE, "18.0", STRING, DERIVED, 1, "version");
    assertThat(version.getRawValue(STRING_TYPE)).isEqualTo("$guavaVersion");
  }

  @Test
  public void testParseCompactNotationClosureWithVariables() throws IOException {
    writeToBuildFile(TestFile.PARSE_COMPACT_NOTATION_CLOSURE_WITH_VARIABLES);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    ArtifactDependencyModel appcompatDependencyModel = dependencies.get(0);
    expected.assertMatches(appcompatDependencyModel);
    assertEquals(expected.compactNotation(), appcompatDependencyModel.compactNotation());
    List<GradlePropertyModel> appcompatResolvedVariables = appcompatDependencyModel.completeModel().getDependencies();
    assertEquals(1, appcompatResolvedVariables.size());

    GradlePropertyModel appcompatVariable = appcompatResolvedVariables.get(0);
    verifyPropertyModel(appcompatVariable, STRING_TYPE, "com.android.support:appcompat-v7:22.1.1", STRING, REGULAR, 0, "appcompat",
                        "ext.appcompat");


    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    ArtifactDependencyModel guavaDependencyModel = dependencies.get(1);
    expected.assertMatches(guavaDependencyModel);
    assertEquals(expected.compactNotation(), guavaDependencyModel.compactNotation());
    List<GradlePropertyModel> guavaResolvedVariables = guavaDependencyModel.completeModel().getDependencies();
    assertEquals(1, guavaResolvedVariables.size());

    GradlePropertyModel guavaVersionVariable = guavaResolvedVariables.get(0);
    verifyPropertyModel(guavaVersionVariable, STRING_TYPE, "18.0", STRING, REGULAR, 0, "guavaVersion", "ext.guavaVersion");
  }

  @Test
  public void testParseMapNotationClosureWithVariables() throws IOException {
    writeToBuildFile(TestFile.PARSE_MAP_NOTATION_CLOSURE_WITH_VARIABLES);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    ArtifactDependencyModel guavaDependencyModel = dependencies.get(0);
    expected.assertMatches(guavaDependencyModel);
    assertEquals(expected.compactNotation(), guavaDependencyModel.compactNotation());
    List<GradlePropertyModel> guavaResolvedVariables = guavaDependencyModel.completeModel().getDependencies();
    assertEquals(1, guavaResolvedVariables.size());

    GradlePropertyModel guavaVersionVariable = guavaResolvedVariables.get(0);
    verifyPropertyModel(guavaVersionVariable, STRING_TYPE, "18.0", STRING, REGULAR, 0, "guavaVersion", "ext.guavaVersion");

    // Now test that resolved variables are not reported for group and name properties.
    GradlePropertyModel group = guavaDependencyModel.group();
    verifyPropertyModel(group, STRING_TYPE, "com.google.guava", STRING, DERIVED, 0, "group");

    GradlePropertyModel name = guavaDependencyModel.name();
    verifyPropertyModel(name, STRING_TYPE, "guava", STRING, DERIVED, 0, "name");

    // and thee guavaVersion variable is reported for version property.
    GradlePropertyModel version = guavaDependencyModel.version();
    verifyPropertyModel(version, STRING_TYPE, "18.0", STRING, DERIVED, 1, "version");
    assertThat(version.getRawValue(STRING_TYPE)).isEqualTo("$guavaVersion");

    guavaVersionVariable = guavaResolvedVariables.get(0);
    verifyPropertyModel(guavaVersionVariable, STRING_TYPE, "18.0", STRING, REGULAR, 0, "guavaVersion", "ext.guavaVersion");
  }

  @Test
  public void testNonDependencyCodeInDependenciesSection() throws IOException {
    writeToBuildFile(TestFile.NON_DEPENDENCY_CODE_IN_DEPENDENCIES_SECTION);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(3);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(RUNTIME, "guava", "com.google.guava", "18.0");
    expected.assertMatches(dependencies.get(1));

    expected = new ExpectedArtifactDependency(TEST_COMPILE, "hibernate", "org.hibernate", "3.1");
    expected.assertMatches(dependencies.get(2));
  }

  @Test
  public void testReplaceDependencyByPsiElement() throws IOException {
    writeToBuildFile(TestFile.REPLACE_DEPENDENCY_BY_PSI_ELEMENT);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(0));

    ArtifactDependencySpec newDep = ArtifactDependencySpec.create("com.google.guava:guava:18.0");
    boolean result = dependenciesModel.replaceArtifactByPsiElement(dependencies.get(0).getPsiElement(), newDep);
    assertTrue(result);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.REPLACE_DEPENDENCY_BY_PSI_ELEMENT_EXPECTED);

    dependencies = buildModel.dependencies().artifacts();
    assertThat(dependencies).hasSize(1);

    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    expected.assertMatches(dependencies.get(0));
  }

  @Test
  public void testReplaceDependencyByChildElement() throws IOException {
    writeToBuildFile(TestFile.REPLACE_DEPENDENCY_BY_CHILD_ELEMENT);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(3);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "appcompat-v7", "com.android.support", "22.1.1");
    expected.assertMatches(dependencies.get(1));

    ArtifactDependencySpec newDep = ArtifactDependencySpec.create("com.google.guava:guava:18.0");
    boolean result = dependenciesModel.replaceArtifactByPsiElement(dependencies.get(1).getPsiElement(), newDep);
    assertTrue(result);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.REPLACE_DEPENDENCY_BY_CHILD_ELEMENT_EXPECTED);

    dependencies = buildModel.dependencies().artifacts();
    assertThat(dependencies).hasSize(3);

    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    expected.assertMatches(dependencies.get(1));
  }

  @Test
  public void testReplaceDependencyFailsIfPsiElementIsNotFound() throws IOException {
    writeToBuildFile(TestFile.REPLACE_DEPENDENCY_FAILS_IF_PSI_ELEMENT_IS_NOT_FOUND);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ArtifactDependencySpec newDep = ArtifactDependencySpec.create("com.google.guava:guava:18.0");
    boolean result = dependenciesModel.replaceArtifactByPsiElement(dependencies.get(0).getPsiElement().getParent().getParent(), newDep);
    assertFalse(result);

    result = dependenciesModel.replaceArtifactByPsiElement(dependencies.get(1).getPsiElement().getContainingFile(), newDep);
    assertFalse(result);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.REPLACE_DEPENDENCY_FAILS_IF_PSI_ELEMENT_IS_NOT_FOUND);

    dependencies = buildModel.dependencies().artifacts();

    // Make sure none of the dependencies have changed.
    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(TEST_COMPILE, "hibernate", "org.hibernate", "3.1");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(TEST_COMPILE, "service", "org.gradle.test.classifiers", "1.0");
    expected.setClassifier("jdk15");
    expected.setExtension("jar");
    expected.assertMatches(dependencies.get(1));
  }

  @Test
  public void testReplaceDependencyUsingMapNotationWithCompactNotation() throws IOException {
    writeToBuildFile(TestFile.REPLACE_DEPENDENCY_USING_MAP_NOTATION_WITH_COMPACT_NOTATION);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "guice", "com.google.code.guice", "1.0");
    expected.assertMatches(dependencies.get(0));

    ArtifactDependencySpec newDep = ArtifactDependencySpec.create("com.google.guava:guava:18.0");
    boolean result = dependenciesModel.replaceArtifactByPsiElement(dependencies.get(0).getPsiElement(), newDep);
    assertTrue(result);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.REPLACE_DEPENDENCY_USING_MAP_NOTATION_WITH_COMPACT_NOTATION_EXPECTED);

    dependencies = buildModel.dependencies().artifacts();

    // Make sure the new dependency is correct.
    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    expected.assertMatches(dependencies.get(0));
  }

  @Test
  public void testReplaceDependencyUsingMapNotationAddingFields() throws IOException {
    writeToBuildFile(TestFile.REPLACE_DEPENDENCY_USING_MAP_NOTATION_ADDING_FIELDS);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "name", null, null);
    expected.assertMatches(dependencies.get(0));

    ArtifactDependencyModel artifactModel = dependencies.get(0);
    assertMissingProperty(artifactModel.group());
    assertMissingProperty(artifactModel.version());
    assertMissingProperty(artifactModel.classifier());
    assertMissingProperty(artifactModel.extension());

    ArtifactDependencySpec newDep = ArtifactDependencySpec.create("com.google.guava:guava:18.0:class@aar");
    boolean result = dependenciesModel.replaceArtifactByPsiElement(dependencies.get(0).getPsiElement(), newDep);
    assertTrue(result);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.REPLACE_DEPENDENCY_USING_MAP_NOTATION_ADDING_FIELDS_EXPECTED);

    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    expected.setClassifier("class");
    expected.setExtension("aar");
    expected.assertMatches(buildModel.dependencies().artifacts().get(0));
  }

  @Test
  public void testReplaceDependencyUsingMapNotationDeleteFields() throws IOException {
    writeToBuildFile(TestFile.REPLACE_DEPENDENCY_USING_MAP_NOTATION_DELETE_FIELDS);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "guice", "com.google.code.guice", "1.0");
    expected.setClassifier("high");
    expected.setExtension("bleh");
    expected.assertMatches(dependencies.get(0));

    ArtifactDependencySpec newDep = ArtifactDependencySpec.create("com.google.guava:guava:+");
    boolean result = dependenciesModel.replaceArtifactByPsiElement(dependencies.get(0).getPsiElement(), newDep);
    assertTrue(result);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.REPLACE_DEPENDENCY_USING_MAP_NOTATION_DELETE_FIELDS_EXPECTED);

    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "+");
    expected.assertMatches(buildModel.dependencies().artifacts().get(0));
  }

  @Test
  public void testReplaceDependencyInArgumentList() throws IOException {
    writeToBuildFile(TestFile.REPLACE_DEPENDENCY_IN_ARGUMENT_LIST);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "guice", "com.google.code.guice", "1.0");
    expected.assertMatches(dependencies.get(0));
    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    expected.assertMatches(dependencies.get(1));

    ArtifactDependencySpec newDep = ArtifactDependencySpec.create("com.google.guava:guava:+");
    boolean result = dependenciesModel.replaceArtifactByPsiElement(dependencies.get(1).getPsiElement(), newDep);
    assertTrue(result);

    applyChangesAndReparse(buildModel);

    expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "+");
    expected.assertMatches(buildModel.dependencies().artifacts().get(1));
  }

  @Test
  public void testReplaceMethodDependencyWithClosure() throws IOException {
    writeToBuildFile(TestFile.REPLACE_METHOD_DEPENDENCY_WITH_CLOSURE);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(TEST_COMPILE, "hibernate", "org.hibernate", "3.1");
    expected.assertMatches(dependencies.get(0));

    ArtifactDependencySpec newDep = ArtifactDependencySpec.create("com.google.guava:guava:+");
    boolean result = dependenciesModel.replaceArtifactByPsiElement(dependencies.get(0).getPsiElement(), newDep);
    assertTrue(result);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.REPLACE_METHOD_DEPENDENCY_WITH_CLOSURE_EXPECTED);

    expected = new ExpectedArtifactDependency(TEST_COMPILE, "guava", "com.google.guava", "+");
    expected.assertMatches(buildModel.dependencies().artifacts().get(0));
  }

  @Test
  public void testReplaceApplicationDependencies() throws IOException {
    writeToBuildFile(TestFile.REPLACE_APPLICATION_DEPENDENCIES);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(2);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(TEST_COMPILE, "service", "org.gradle.test.classifiers", "1.0");
    expected.assertMatches(dependencies.get(0));

    expected = new ExpectedArtifactDependency(TEST_COMPILE, "guava", "com.google.guava", "+");
    expected.assertMatches(dependencies.get(1));

    ArtifactDependencySpec newDep = ArtifactDependencySpec.create("org.hibernate:hibernate:3.1");
    boolean result = dependenciesModel.replaceArtifactByPsiElement(dependencies.get(0).getPsiElement(), newDep);
    assertTrue(result);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.REPLACE_APPLICATION_DEPENDENCIES_EXPECTED);

    expected = new ExpectedArtifactDependency(TEST_COMPILE, "hibernate", "org.hibernate", "3.1");
    expected.assertMatches(buildModel.dependencies().artifacts().get(0));
  }

  @Test
  public void testDeleteGroupAndVersion() throws IOException {
    writeToBuildFile(TestFile.DELETE_GROUP_AND_VERSION);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(2, artifacts);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(TEST_COMPILE, "service", "org.gradle.test.classifiers", "1.0");
    expected.assertMatches(artifacts.get(0));
    expected = new ExpectedArtifactDependency(TEST_COMPILE, "guava", "com.google.guava", "+");
    expected.assertMatches(artifacts.get(1));

    // Remove version from the first artifact and group from the second. Even though these now become invalid dependencies we should still
    // allow them from the model.
    ArtifactDependencyModel first = artifacts.get(0);
    first.version().delete();
    ArtifactDependencyModel second = artifacts.get(1);
    second.group().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.DELETE_GROUP_AND_VERSION_EXPECTED);

    dependencies = buildModel.dependencies();
    artifacts = dependencies.artifacts();

    first = artifacts.get(0);
    assertThat(first.completeModel().toString()).isEqualTo("org.gradle.test.classifiers:service");
    second = artifacts.get(1);
    assertThat(second.completeModel().toString()).isEqualTo("guava:+");
  }

  @Test
  public void testDeleteNameAndRenameUnsupported() throws IOException {
    writeToBuildFile(TestFile.DELETE_NAME_AND_RENAME_UNSUPPORTED);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(1, artifacts);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(TEST_COMPILE, "service", "org.gradle.test.classifiers", "1.0");
    expected.assertMatches(artifacts.get(0));

    ArtifactDependencyModel first = artifacts.get(0);
    try {
      first.name().delete();
      fail();
    }
    catch (UnsupportedOperationException e) {
      // Expected
    }

    try {
      first.name().rename("Hello");
      fail();
    }
    catch (UnsupportedOperationException e) {
      // Expected
    }

    assertFalse(buildModel.isModified());
  }

  @Test
  public void testDeleteInMethodCallWithProperties() throws IOException {
    writeToBuildFile(TestFile.DELETE_IN_METHOD_CALL_WITH_PROPERTIES);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(2, artifacts);

    // Attempt to delete the first one.
    ArtifactDependencyModel first = artifacts.get(0);
    first.completeModel().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.DELETE_IN_METHOD_CALL_WITH_PROPERTIES_EXPECTED);

    buildModel = getGradleBuildModel();
    dependencies = buildModel.dependencies();

    artifacts = dependencies.artifacts();
    assertSize(1, artifacts);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency(COMPILE, "guava", "com.google.guava", "18.0");
    expected.assertMatches(artifacts.get(0));
  }

  @Test
  public void testMissingPropertiesCompact() throws IOException {
    writeToBuildFile(TestFile.MISSING_PROPERTIES_COMPACT);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(1, artifacts);

    ArtifactDependencyModel artifact = artifacts.get(0);
    assertMissingProperty(artifact.extension());
    assertMissingProperty(artifact.classifier());
    verifyPropertyModel(artifact.name(), STRING_TYPE, "appcompat-v7", STRING, FAKE, 0, "name");
    verifyPropertyModel(artifact.group(), STRING_TYPE, "com.android.support", STRING, FAKE, 0, "group");
    verifyPropertyModel(artifact.version(), STRING_TYPE, "22.1.1", STRING, FAKE, 0, "version");

    verifyPropertyModel(artifact.completeModel(), STRING_TYPE, "com.android.support:appcompat-v7:22.1.1", STRING, REGULAR, 0, "compile");
  }

  @Test
  public void testMissingPropertiesMap() throws IOException {
    writeToBuildFile(TestFile.MISSING_PROPERTIES_MAP);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(1, artifacts);

    ArtifactDependencyModel artifact = artifacts.get(0);
    assertMissingProperty(artifact.extension());
    assertMissingProperty(artifact.classifier());
    assertMissingProperty(artifact.version());
    assertMissingProperty(artifact.extension());
    verifyPropertyModel(artifact.name(), STRING_TYPE, "name", STRING, DERIVED, 0, "name");

    verifyMapProperty(artifact.completeModel(), ImmutableMap.of("name", "name"), "compile", "dependencies.compile");
  }

  @Test
  public void testCompactNotationPsiElement() throws IOException {
    writeToBuildFile(TestFile.COMPACT_NOTATION_PSI_ELEMENT);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(1, artifacts);

    ArtifactDependencyModel artifact = artifacts.get(0);
    PsiElement psiElement = artifact.getPsiElement();
    if (isGroovy()) {
      assertThat(psiElement).isInstanceOf(GrLiteral.class);
      assertThat(psiElement.getText()).isEqualTo("'org.gradle.test.classifiers:service:1.0'");
    }
    else {
      assertThat(psiElement).isInstanceOf(KtStringTemplateExpression.class);
      assertThat(psiElement.getText()).isEqualTo("\"org.gradle.test.classifiers:service:1.0\"");
    }
  }

  @Test
  public void testMultipleCompactNotationPsiElements() throws IOException {
    isIrrelevantForKotlinScript("No multiple dependency configuration form in KotlinScript");
    writeToBuildFile(TestFile.MULTIPLE_COMPACT_NOTATION_PSI_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(2, artifacts);

    ArtifactDependencyModel artifact = artifacts.get(0);
    PsiElement psiElement = artifact.getPsiElement();
    if (isGroovy()) {
      assertThat(psiElement).isInstanceOf(GrLiteral.class);
    }
    assertThat(psiElement.getText()).isEqualTo("'com.google.code.guice:guice:1.0'");

    artifact = artifacts.get(1);
    psiElement = artifact.getPsiElement();
    if (isGroovy()) {
      assertThat(psiElement).isInstanceOf(GrLiteral.class);
    }
    assertThat(psiElement.getText()).isEqualTo("'com.google.guava:guava:18.0'");
  }

  @Test
  public void testMethodCallCompactPsiElement() throws IOException {
    writeToBuildFile(TestFile.METHOD_CALL_COMPACT_PSI_ELEMENT);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(1, artifacts);

    ArtifactDependencyModel artifact = artifacts.get(0);
    PsiElement psiElement = artifact.getPsiElement();
    if (isGroovy()) {
      assertThat(psiElement).isInstanceOf(GrLiteral.class);
      assertThat(psiElement.getText()).isEqualTo("'org.hibernate:hibernate:3.1'");
    }
    else {
      assertThat(psiElement).isInstanceOf(KtStringTemplateExpression.class);
      assertThat(psiElement.getText()).isEqualTo("\"org.hibernate:hibernate:3.1\"");
    }
  }

  @Test
  public void testMethodCallMultipleCompactPsiElement() throws IOException {
    isIrrelevantForKotlinScript("No multiple dependency configuration form in KotlinScript");
    writeToBuildFile(TestFile.METHOD_CALL_MULTIPLE_COMPACT_PSI_ELEMENT);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(2, artifacts);

    ArtifactDependencyModel artifact = artifacts.get(0);
    PsiElement psiElement = artifact.getPsiElement();
    if (isGroovy()) {
      assertThat(psiElement).isInstanceOf(GrLiteral.class);
    }
    assertThat(psiElement.getText()).isEqualTo("'com.google.code.guice:guice:1.0'");

    artifact = artifacts.get(1);
    psiElement = artifact.getPsiElement();
    if (isGroovy()) {
      assertThat(psiElement).isInstanceOf(GrLiteral.class);
    }
    assertThat(psiElement.getText()).isEqualTo("'com.google.guava:guava:18.0'");
  }

  @Test
  public void testMapNotationPsiElement() throws IOException {
    writeToBuildFile(TestFile.MAP_NOTATION_PSI_ELEMENT);
    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(1, artifacts);

    ArtifactDependencyModel artifact = artifacts.get(0);
    PsiElement psiElement = artifact.getPsiElement();
    if (isGroovy()) {
      assertThat(psiElement).isInstanceOf(GrArgumentList.class);
      assertThat(psiElement.getText())
        .isEqualTo("group: 'com.google.code.guice', name: 'guice', version: '1.0', classifier: 'high', ext: 'bleh'");
    }
    else {
      assertThat(psiElement).isInstanceOf(KtCallExpression.class);
      assertThat(psiElement.getText())
        .isEqualTo("compile(group=\"com.google.code.guice\", name=\"guice\", version=\"1.0\", classifier=\"high\", ext=\"bleh\")");
    }
  }

  @Test
  public void testCompactNotationSetToReference() throws IOException {
    writeToBuildFile(TestFile.COMPACT_NOTATION_SET_TO_REFERENCE);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(2, artifacts);

    // Get the version variable model
    ExtModel extModel = buildModel.ext();
    GradlePropertyModel name = extModel.findProperty("name");

    ArtifactDependencyModel firstModel = artifacts.get(0);
    firstModel.version().setValue(new ReferenceTo("version"));
    ArtifactDependencyModel secondModel = artifacts.get(1);
    secondModel.name().setValue(new ReferenceTo(name));

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.COMPACT_NOTATION_SET_TO_REFERENCE_EXPECTED);

    dependencies = buildModel.dependencies();
    artifacts = dependencies.artifacts();
    assertSize(2, artifacts);

    firstModel = artifacts.get(0);
    verifyPropertyModel(firstModel.version(), STRING_TYPE, "3.6", STRING, FAKE, 1, "version");
    assertThat(firstModel.completeModel().getRawValue(STRING_TYPE)).isEqualTo("org.gradle.test.classifiers:service:${version}");

    secondModel = artifacts.get(1);
    verifyPropertyModel(secondModel.name(), STRING_TYPE, "guava", STRING, FAKE, 1, "name");
    assertThat(secondModel.completeModel().getRawValue(STRING_TYPE)).isEqualTo("com.google.guava:${name}:+");
  }

  @Test
  public void testCompactNotationElementUnsupportedOperations() throws IOException {
    writeToBuildFile(TestFile.COMPACT_NOTATION_ELEMENT_UNSUPPORTED_OPERATIONS);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    ArtifactDependencyModel artifact = artifacts.get(0);
    try {
      artifact.version().rename("hello"); // This doesn't make sense
      fail();
    }
    catch (UnsupportedOperationException e) {
      // Expected
    }

    try {
      artifact.name().delete(); // Names can't be deleted
      fail();
    }
    catch (UnsupportedOperationException e) {
      // Expected
    }

    try {
      artifact.classifier().convertToEmptyMap(); // Again this operation doesn't make sense
      fail();
    }
    catch (UnsupportedOperationException e) {
      // Expected
    }

    try {
      artifact.extension().convertToEmptyList();
      fail();
    }
    catch (UnsupportedOperationException e) {
      // Expected
    }

    assertFalse(buildModel.isModified());
  }

  @Test
  public void testSetIStrInCompactNotation() throws IOException {
    writeToBuildFile(TestFile.SET_I_STR_IN_COMPACT_NOTATION);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    ArtifactDependencyModel artifact = artifacts.get(0);

    artifact.version().setValue(iStr("3.2.1"));

    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies();

    artifacts = dependencies.artifacts();
    artifact = artifacts.get(0);
    assertThat(artifact.completeModel().toString()).isEqualTo("org.gradle.test.classifiers:service:3.2.1");
    verifyFileContents(myBuildFile, TestFile.SET_I_STR_IN_COMPACT_NOTATION_EXPECTED);
  }

  @Test
  public void testParseFullReferencesCompactApplication() throws IOException {
    writeToBuildFile(TestFile.PARSE_FULL_REFERENCES_COMPACT_APPLICATION);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(2, artifacts);

    assertThat(artifacts.get(0).configurationName()).isEqualTo("testCompile");
    assertThat(artifacts.get(0).configurationName()).isEqualTo("testCompile");
    ResolvedPropertyModel model0 = artifacts.get(0).completeModel();
    ResolvedPropertyModel model1 = artifacts.get(1).completeModel();
    if (isGroovy()) {
      verifyPropertyModel(model0, STRING_TYPE, "org.gradle.test.classifiers:service:1.0", STRING, DERIVED, 1, "0");
      verifyPropertyModel(model1, STRING_TYPE, "com.google.guava:guava:+", STRING, DERIVED, 1, "1");
    }
    else {
      verifyPropertyModel(model0, STRING_TYPE, "org.gradle.test.classifiers:service:1.0", STRING, REGULAR, 1, "testCompile");
      verifyPropertyModel(model1, STRING_TYPE, "com.google.guava:guava:+", STRING, REGULAR, 1, "testCompile");
    }
  }

  private void runSetFullReferencesTest(@NotNull TestFileName testFileName, @NotNull TestFileName expected) throws IOException {
    writeToBuildFile(testFileName);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(2, artifacts);

    ResolvedPropertyModel model = artifacts.get(0).completeModel();
    model.setValue(new ReferenceTo("service"));
    model = artifacts.get(1).completeModel();
    model.setValue(iStr("com.${guavaPart}"));

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, expected);

    dependencies = buildModel.dependencies();
    artifacts = dependencies.artifacts();
    assertSize(2, artifacts);

    assertThat(artifacts.get(0).configurationName()).isEqualTo("testCompile");
    assertThat(artifacts.get(0).configurationName()).isEqualTo("testCompile");
    ResolvedPropertyModel model0 = artifacts.get(0).completeModel();
    ResolvedPropertyModel model1 = artifacts.get(1).completeModel();
    if (isGroovy()) {
      verifyPropertyModel(model0, STRING_TYPE, "org.gradle.test.classifiers:service:1.0", STRING, DERIVED, 1, "0");
      verifyPropertyModel(model1, STRING_TYPE, "com.google.guava:guava:+", STRING, DERIVED, 1, "1");
    }
    else {
      verifyPropertyModel(model0, STRING_TYPE, "org.gradle.test.classifiers:service:1.0", STRING, REGULAR, 1, "testCompile");
      verifyPropertyModel(model1, STRING_TYPE, "com.google.guava:guava:+", STRING, REGULAR, 1, "testCompile");
    }
  }

  private void runSetFullSingleReferenceTest(@NotNull TestFileName testFileName, @NotNull PropertyType type, @NotNull String name)
    throws IOException {
    writeToBuildFile(testFileName);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(1, artifacts);

    ResolvedPropertyModel model = artifacts.get(0).completeModel();
    model.setValue(new ReferenceTo("service"));

    applyChangesAndReparse(buildModel);

    buildModel = getGradleBuildModel();
    dependencies = buildModel.dependencies();
    artifacts = dependencies.artifacts();
    assertSize(1, artifacts);

    model = artifacts.get(0).completeModel();
    verifyPropertyModel(model, STRING_TYPE, "org.gradle.test.classifiers:service:1.0", STRING, type, 1, name);
    assertThat(artifacts.get(0).configurationName()).isEqualTo("testCompile");
  }

  @Test
  public void testSetSingleReferenceCompactApplication() throws IOException {
    runSetFullSingleReferenceTest(TestFile.SET_SINGLE_REFERENCE_COMPACT_APPLICATION, REGULAR, "testCompile");
  }

  @Test
  public void testSetSingleReferenceCompactMethod() throws IOException {
    isIrrelevantForKotlinScript("no distinct method form of dependency configuation in KotlinScript");
    // Properties from within method calls are derived.
    runSetFullSingleReferenceTest(TestFile.SET_SINGLE_REFERENCE_COMPACT_METHOD, DERIVED, "0");
  }

  @Test
  public void testSetFullReferencesCompactApplication() throws IOException {
    runSetFullReferencesTest(TestFile.SET_FULL_REFERENCES_COMPACT_APPLICATION, TestFile.SET_FULL_REFERENCES_COMPACT_APPLICATION_EXPECTED);
  }

  @Test
  public void testSetFullReferenceCompactMethod() throws IOException {
    isIrrelevantForKotlinScript("no distinction between method and application form");
    runSetFullReferencesTest(TestFile.SET_FULL_REFERENCE_COMPACT_METHOD, TestFile.SET_FULL_REFERENCE_COMPACT_METHOD_EXPECTED);
  }

  @Test
  public void testParseFullReferenceMap() throws IOException {
    writeToBuildFile(TestFile.PARSE_FULL_REFERENCE_MAP);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(4, artifacts);

    ResolvedPropertyModel model = artifacts.get(0).completeModel();
    verifyMapProperty(model, ImmutableMap.of("group", "group", "name", "name", "version", "1.0"));
    assertThat(artifacts.get(0).configurationName()).isEqualTo("testCompile");
    model = artifacts.get(1).completeModel();
    verifyMapProperty(model, ImmutableMap.of("group", "com.google.guava", "name", "guava", "version", "4.0"));
    assertThat(artifacts.get(1).configurationName()).isEqualTo("compile");
    model = artifacts.get(2).completeModel();
    verifyMapProperty(model, ImmutableMap.of("group", "g", "name", "n", "version", "2.0"));
    assertThat(artifacts.get(2).configurationName()).isEqualTo("testCompile");
    model = artifacts.get(3).completeModel();
    verifyMapProperty(model, ImmutableMap.of("group", "guava", "name", "com.google.guava", "version", "3.0"));
    assertThat(artifacts.get(3).configurationName()).isEqualTo("compile");
  }

  @Test
  public void testSetFullReferenceMap() throws IOException {
    assumeTrue("KotlinScript writer emits stray punctuation for map references", !isKotlinScript()); // TODO(b/155168920)
    writeToBuildFile(TestFile.SET_FULL_REFERENCE_MAP);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependencies = buildModel.dependencies();
    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertSize(4, artifacts);

    ArtifactDependencyModel model = artifacts.get(0);
    model.completeModel().setValue(new ReferenceTo("dependency"));
    model = artifacts.get(1);
    model.group().setValue(new ReferenceTo("guavaGroup"));
    model.name().setValue(new ReferenceTo("guavaName"));
    model = artifacts.get(2);
    model.completeModel().setValue(new ReferenceTo("otherDependency"));
    model = artifacts.get(3);
    model.group().setValue(new ReferenceTo("guavaName"));
    model.name().setValue(new ReferenceTo("guavaGroup"));

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.SET_FULL_REFERENCE_MAP_EXPECTED);

    dependencies = buildModel.dependencies();
    artifacts = dependencies.artifacts();
    assertSize(4, artifacts);

    model = artifacts.get(0);
    verifyMapProperty(model.completeModel(), ImmutableMap.of("group", "group", "name", "name", "version", "1.0"));
    assertThat(artifacts.get(0).configurationName()).isEqualTo("testCompile");
    model = artifacts.get(1);
    verifyMapProperty(model.completeModel(), ImmutableMap.of("group", "com.google.guava", "name", "guava", "version", "4.0"));
    assertThat(artifacts.get(1).configurationName()).isEqualTo("compile");
    model = artifacts.get(2);
    verifyMapProperty(model.completeModel(), ImmutableMap.of("group", "g", "name", "n", "version", "2.0"));
    model = artifacts.get(3);
    verifyMapProperty(model.completeModel(), ImmutableMap.of("group", "guava", "name", "com.google.guava", "version", "3.0"));
  }

  @Test
  public void testCorrectObtainResultModel() throws IOException {
    writeToBuildFile(TestFile.CORRECT_OBTAIN_RESULT_MODEL);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel depModel = buildModel.dependencies();

    ArtifactDependencyModel adm = depModel.artifacts().get(0);

    verifyPropertyModel(adm.version().getResultModel(), STRING_TYPE, "2.3", STRING, REGULAR, 0, "jUnitVersion");
  }

  @Test
  public void testSetVersionReference() throws IOException {
    writeToBuildFile(TestFile.SET_VERSION_REFERENCE);

    GradleBuildModel model = getGradleBuildModel();
    DependenciesModel depModel = model.dependencies();

    String compactNotation = "junit:junit:${jUnitVersion}";
    depModel.addArtifact("implementation", compactNotation);
    ArtifactDependencyModel artModel = depModel.artifacts().get(0);
    verifyPropertyModel(artModel.completeModel().resolve(), STRING_TYPE, "junit:junit:2", STRING, REGULAR, 1);
    verifyPropertyModel(artModel.version().getResultModel(), INTEGER_TYPE, 2, INTEGER, REGULAR, 0);

    applyChangesAndReparse(model);
    depModel = model.dependencies();
    artModel = depModel.artifacts().get(0);
    verifyPropertyModel(artModel.completeModel().resolve(), STRING_TYPE, "junit:junit:2", STRING, REGULAR, 1);
    verifyPropertyModel(artModel.version().getResultModel(), INTEGER_TYPE, 2, INTEGER, REGULAR, 0);

    verifyFileContents(myBuildFile, TestFile.SET_VERSION_REFERENCE_EXPECTED);
  }

  @Test
  public void testSetExcludesBlockToReferences() throws IOException {
    writeToBuildFile(TestFile.SET_EXCLUDES_BLOCK_TO_REFERENCES);

    GradleBuildModel buildModel = getGradleBuildModel();
    ArtifactDependencySpec spec = ArtifactDependencySpec.create("junit:junit:$junit_version");
    ArtifactDependencySpec excludesSpec = ArtifactDependencySpec.create("$excludes_group:$excludes_name");
    buildModel.dependencies().addArtifact("implementation", spec, ImmutableList.of(excludesSpec));

    // Dependency configuration blocks are not supported before applying and re-parsing.

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.SET_EXCLUDES_BLOCK_TO_REFERENCES_EXPECTED);

    ArtifactDependencyModel artifactModel = buildModel.dependencies().artifacts().get(0);
    ExcludedDependencyModel excludedModel = artifactModel.configuration().excludes().get(0);

    verifyPropertyModel(excludedModel.group(), STRING_TYPE, "dependency", STRING, DERIVED, 1);
    verifyPropertyModel(excludedModel.module(), STRING_TYPE, "bad", STRING, DERIVED, 1);
    verifyPropertyModel(artifactModel.completeModel(), STRING_TYPE, "junit:junit:2.3.1", STRING, REGULAR, 1);
  }

  @Test
  public void testSetThroughMapReference() throws IOException {
    writeToBuildFile(TestFile.SET_THROUGH_MAP_REFERENCE);

    GradleBuildModel buildModel = getGradleBuildModel();

    ArtifactDependencyModel artModel = buildModel.dependencies().artifacts().get(0);
    verifyMapProperty(artModel.completeModel().getResultModel(), ImmutableMap.of("name", "awesome", "group", "some", "version", "1.0"));

    artModel.name().setValue("boo");
    artModel.group().setValue("spooky");
    artModel.version().setValue("2.0");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.SET_THROUGH_MAP_REFERENCE_EXPECTED);

    artModel = buildModel.dependencies().artifacts().get(0);
    verifyMapProperty(artModel.completeModel().getResultModel(), ImmutableMap.of("name", "boo", "group", "spooky", "version", "2.0"));
  }

  @Test
  public void testMalformedFakeArtifactElement() throws IOException {
    writeToBuildFile(TestFile.MALFORMED_FAKE_ARTIFACT_ELEMENT);

    GradleBuildModel buildModel = getGradleBuildModel();

    assertEmpty(buildModel.dependencies().artifacts());
  }

  @Test
  public void testCompactSetThroughReferences() throws IOException {
    writeToBuildFile(TestFile.COMPACT_SET_THROUGH_REFERENCES);

    GradleBuildModel buildModel = getGradleBuildModel();

    ArtifactDependencyModel artModel = buildModel.dependencies().artifacts().get(0);
    verifyPropertyModel(artModel.completeModel().resolve(), STRING_TYPE, "a:b:1.0", STRING, REGULAR, 1);

    artModel.enableSetThrough();
    artModel.version().setValue("2.0");
    artModel.name().setValue("c");
    artModel.group().setValue("d");
    artModel.disableSetThrough();
    artModel.group().setValue("e");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.COMPACT_SET_THROUGH_REFERENCES_EXPECTED);

    artModel = buildModel.dependencies().artifacts().get(0);
    verifyPropertyModel(artModel.completeModel().resolve(), STRING_TYPE, "e:c:2.0", STRING, REGULAR, 0);
    verifyPropertyModel(buildModel.ext().findProperty("dep").resolve(), STRING_TYPE, "d:c:2.0", STRING, REGULAR, 0);
  }

  @Test
  public void testEmptyFakeArtifactElement() throws IOException {
    writeToBuildFile(TestFile.EMPTY_FAKE_ARTIFACT_ELEMENT);

    GradleBuildModel buildModel = getGradleBuildModel();

    assertSize(0, buildModel.dependencies().all());
  }

  @Test
  public void testFollowMultipleReferences() throws IOException {
    writeToBuildFile(TestFile.FOLLOW_MULTIPLE_REFERENCES);

    GradleBuildModel buildModel = getGradleBuildModel();
    ArtifactDependencyModel artModel = buildModel.dependencies().artifacts().get(0);
    verifyPropertyModel(artModel.completeModel().resolve(), STRING_TYPE, "a:b:1.0", STRING, REGULAR, 1);

    artModel.enableSetThrough();
    artModel.name().setValue("z");

    applyChangesAndReparse(buildModel);

    verifyPropertyModel(artModel.completeModel().resolve(), STRING_TYPE, "a:z:1.0", STRING, REGULAR, 1);
  }

  @Test
  public void testArtifactNotationEdgeCases() throws IOException {
    writeToBuildFile(TestFile.ARTIFACT_NOTATION_EDGE_CASES);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ArtifactDependencyModel> artifacts = buildModel.dependencies().artifacts();
    assertSize(2, artifacts);

    ArtifactDependencyModel firstArtifact = artifacts.get(0);
    assertEquals("artifact", firstArtifact.name().toString());
    assertEquals("com.cool.company", firstArtifact.group().toString());
    assertMissingProperty(firstArtifact.version());

    ArtifactDependencyModel secondArtifact = artifacts.get(1);
    assertNotNull(secondArtifact.name());
  }

  @Test
  public void testSetConfigurationWhenSingle() throws Exception {
    writeToBuildFile(TestFile.SET_CONFIGURATION_WHEN_SINGLE);
    GradleBuildModel buildModel = getGradleBuildModel();

    List<ArtifactDependencyModel> artifacts = buildModel.dependencies().artifacts();
    assertSize(7, artifacts);

    assertThat(artifacts.get(0).configurationName()).isEqualTo("test");
    artifacts.get(0).setConfigurationName("androidTest");
    assertThat(artifacts.get(0).configurationName()).isEqualTo("androidTest");

    assertThat(artifacts.get(1).configurationName()).isEqualTo("compile");
    artifacts.get(1).setConfigurationName("zapi");
    assertThat(artifacts.get(1).configurationName()).isEqualTo("zapi");
    artifacts.get(1).setConfigurationName("api"); // Try twice.
    assertThat(artifacts.get(1).configurationName()).isEqualTo("api");

    assertThat(artifacts.get(2).configurationName()).isEqualTo("api");
    artifacts.get(2).setConfigurationName("zompile");
    assertThat(artifacts.get(2).configurationName()).isEqualTo("zompile");
    artifacts.get(2).setConfigurationName("compile"); // Try twice
    assertThat(artifacts.get(2).configurationName()).isEqualTo("compile");

    assertThat(artifacts.get(3).configurationName()).isEqualTo("testCompile");
    artifacts.get(3).setConfigurationName("testImplementation");
    assertThat(artifacts.get(3).configurationName()).isEqualTo("testImplementation");

    assertThat(artifacts.get(4).configurationName()).isEqualTo("implementation");
    artifacts.get(4).setConfigurationName("debugImplementation");
    assertThat(artifacts.get(4).configurationName()).isEqualTo("debugImplementation");

    assertThat(artifacts.get(5).configurationName()).isEqualTo("api");
    artifacts.get(5).setConfigurationName("implementation");
    assertThat(artifacts.get(5).configurationName()).isEqualTo("implementation");

    assertThat(artifacts.get(6).configurationName()).isEqualTo("debug");
    artifacts.get(6).setConfigurationName("prerelease");
    assertThat(artifacts.get(6).configurationName()).isEqualTo("prerelease");
    artifacts.get(6).setConfigurationName("release");
    assertThat(artifacts.get(6).configurationName()).isEqualTo("release");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.SET_CONFIGURATION_WHEN_SINGLE_EXPECTED);

    artifacts = buildModel.dependencies().artifacts();
    assertSize(7, artifacts);

    assertThat(artifacts.get(0).configurationName()).isEqualTo("androidTest");
    assertThat(artifacts.get(0).compactNotation()).isEqualTo("org.gradle.test.classifiers:service:1.0:jdk15@jar");

    assertThat(artifacts.get(1).configurationName()).isEqualTo("api");
    assertThat(artifacts.get(1).compactNotation()).isEqualTo("a:b:1.0");

    assertThat(artifacts.get(2).configurationName()).isEqualTo("compile");
    assertThat(artifacts.get(2).compactNotation()).isEqualTo("a:b:1.0");

    assertThat(artifacts.get(3).configurationName()).isEqualTo("testImplementation");
    assertThat(artifacts.get(3).compactNotation()).isEqualTo("org.hibernate:hibernate:3.1");
    assertThat(artifacts.get(3).configuration().force().toBoolean()).isEqualTo(true);

    assertThat(artifacts.get(4).configurationName()).isEqualTo("debugImplementation");
    assertThat(artifacts.get(4).compactNotation()).isEqualTo("com.example:artifact:1.0");

    assertThat(artifacts.get(5).configurationName()).isEqualTo("implementation");
    assertThat(artifacts.get(5).compactNotation()).isEqualTo("org.example:artifact:2.0");

    assertThat(artifacts.get(6).configurationName()).isEqualTo("release");
    assertThat(artifacts.get(6).compactNotation()).isEqualTo("org.example:artifact:2.0");
    assertThat(artifacts.get(6).configuration().force().toBoolean()).isEqualTo(true);
  }

  @Test
  public void testSetConfigurationWhenMultiple() throws Exception {
    isIrrelevantForKotlinScript("No multiple dependency configuration form in KotlinScript");
    writeToBuildFile(TestFile.SET_CONFIGURATION_WHEN_MULTIPLE);
    GradleBuildModel buildModel = getGradleBuildModel();

    List<ArtifactDependencyModel> artifacts = buildModel.dependencies().artifacts();
    assertSize(11, artifacts);
    assertThat(artifacts.get(0).configurationName()).isEqualTo("test");
    assertThat(artifacts.get(0).compactNotation()).isEqualTo("org.gradle.test.classifiers:service:1.0:jdk15@jar");

    assertThat(artifacts.get(1).configurationName()).isEqualTo("test");
    assertThat(artifacts.get(1).compactNotation()).isEqualTo("org.example:artifact:2.0");

    assertThat(artifacts.get(2).configurationName()).isEqualTo("compile");
    assertThat(artifacts.get(2).compactNotation()).isEqualTo("com.example:artifact:1.0");

    assertThat(artifacts.get(3).configurationName()).isEqualTo("compile");
    assertThat(artifacts.get(3).compactNotation()).isEqualTo("com.android.support:appcompat-v7:22.1.1");

    assertThat(artifacts.get(4).configurationName()).isEqualTo("implementation");
    assertThat(artifacts.get(4).compactNotation()).isEqualTo("com.example:artifact:1.0");

    assertThat(artifacts.get(5).configurationName()).isEqualTo("implementation");
    assertThat(artifacts.get(5).compactNotation()).isEqualTo("org.hibernate:hibernate:3.1");

    assertThat(artifacts.get(6).configurationName()).isEqualTo("implementation");
    assertThat(artifacts.get(6).compactNotation()).isEqualTo("org.example:artifact:2.0");

    assertThat(artifacts.get(7).configurationName()).isEqualTo("releaseImplementation");
    assertThat(artifacts.get(7).compactNotation()).isEqualTo("com.example.libs:lib1:2.0");

    assertThat(artifacts.get(8).configurationName()).isEqualTo("releaseImplementation");
    assertThat(artifacts.get(8).compactNotation()).isEqualTo("com.example.libs:lib2:1.0");

    assertThat(artifacts.get(9).configurationName()).isEqualTo("api");
    assertThat(artifacts.get(9).compactNotation()).isEqualTo("com.example.libs:lib3:2.0");

    assertThat(artifacts.get(10).configurationName()).isEqualTo("api");
    assertThat(artifacts.get(10).compactNotation()).isEqualTo("com.example.libs:lib4:1.0");

    {
      artifacts.get(1).setConfigurationName("androidTest");
      List<ArtifactDependencyModel> updatedArtifacts = buildModel.dependencies().artifacts();
      assertSize(11, updatedArtifacts);
      // Note: The renamed element becomes the first in the group.
      assertThat(updatedArtifacts.get(0).configurationName()).isEqualTo("androidTest");
      assertThat(updatedArtifacts.get(0).compactNotation()).isEqualTo("org.example:artifact:2.0");

      assertThat(updatedArtifacts.get(1).configurationName()).isEqualTo("test");
      assertThat(updatedArtifacts.get(1).compactNotation()).isEqualTo("org.gradle.test.classifiers:service:1.0:jdk15@jar");
    }

    {
      // Rename both elements of the same group and rename some of them twice.
      artifacts.get(2).setConfigurationName("zapi");
      artifacts.get(2).setConfigurationName("api");
      artifacts.get(3).setConfigurationName("zimplementation");
      artifacts.get(3).setConfigurationName("implementation");
      List<ArtifactDependencyModel> updatedArtifacts = buildModel.dependencies().artifacts();
      assertSize(11, updatedArtifacts);
      // Note: The renamed element becomes the first in the group.
      assertThat(updatedArtifacts.get(2).configurationName()).isEqualTo("api");
      assertThat(updatedArtifacts.get(2).compactNotation()).isEqualTo("com.example:artifact:1.0");

      assertThat(updatedArtifacts.get(3).configurationName()).isEqualTo("implementation");
      assertThat(updatedArtifacts.get(3).compactNotation()).isEqualTo("com.android.support:appcompat-v7:22.1.1");
    }

    {
      artifacts.get(6).setConfigurationName("zapi");
      artifacts.get(6).setConfigurationName("api");
      List<ArtifactDependencyModel> updatedArtifacts = buildModel.dependencies().artifacts();
      assertSize(11, updatedArtifacts);
      // Note: The renamed element becomes the first in the group.
      assertThat(updatedArtifacts.get(4).configurationName()).isEqualTo("api");
      assertThat(updatedArtifacts.get(4).compactNotation()).isEqualTo("org.example:artifact:2.0");
      assertThat(updatedArtifacts.get(4).version().getUnresolvedModel().getValueType()).isEqualTo(REFERENCE);

      assertThat(updatedArtifacts.get(5).configurationName()).isEqualTo("implementation");
      assertThat(updatedArtifacts.get(5).compactNotation()).isEqualTo("com.example:artifact:1.0");

      assertThat(updatedArtifacts.get(6).configurationName()).isEqualTo("implementation");
      assertThat(updatedArtifacts.get(6).compactNotation()).isEqualTo("org.hibernate:hibernate:3.1");
    }

    {
      try {
        // Try an unsupported case.
        artifacts.get(7).setConfigurationName("debugImplementation1");
        fail();
      }
      catch (UnsupportedOperationException e) {
      }
    }
    {
      artifacts.get(10).setConfigurationName("debugApi1");
      artifacts.get(10).setConfigurationName("debugApi");
      List<ArtifactDependencyModel> updatedArtifacts = buildModel.dependencies().artifacts();
      assertSize(11, updatedArtifacts);
      // Note: The renamed element becomes the first in the group.
      assertThat(updatedArtifacts.get(9).configurationName()).isEqualTo("debugApi");
      assertThat(updatedArtifacts.get(9).compactNotation()).isEqualTo("com.example.libs:lib4:1.0");

      assertThat(updatedArtifacts.get(10).configurationName()).isEqualTo("api");
      assertThat(updatedArtifacts.get(10).compactNotation()).isEqualTo("com.example.libs:lib3:2.0");
    }

    applyChangesAndReparse(buildModel);

    artifacts = buildModel.dependencies().artifacts();
    assertSize(11, artifacts);

    assertThat(artifacts.get(0).configurationName()).isEqualTo("androidTest");
    assertThat(artifacts.get(0).compactNotation()).isEqualTo("org.example:artifact:2.0");

    assertThat(artifacts.get(1).configurationName()).isEqualTo("test");
    assertThat(artifacts.get(1).compactNotation()).isEqualTo("org.gradle.test.classifiers:service:1.0:jdk15@jar");

    assertThat(artifacts.get(2).configurationName()).isEqualTo("api");
    assertThat(artifacts.get(2).compactNotation()).isEqualTo("com.example:artifact:1.0");

    assertThat(artifacts.get(3).configurationName()).isEqualTo("implementation");
    assertThat(artifacts.get(3).compactNotation()).isEqualTo("com.android.support:appcompat-v7:22.1.1");

    assertThat(artifacts.get(4).configurationName()).isEqualTo("api");
    assertThat(artifacts.get(4).compactNotation()).isEqualTo("org.example:artifact:2.0");
    assertThat(artifacts.get(4).version().getUnresolvedModel().getValueType()).isEqualTo(REFERENCE);

    assertThat(artifacts.get(5).configurationName()).isEqualTo("implementation");
    assertThat(artifacts.get(5).compactNotation()).isEqualTo("com.example:artifact:1.0");

    assertThat(artifacts.get(6).configurationName()).isEqualTo("implementation");
    assertThat(artifacts.get(6).compactNotation()).isEqualTo("org.hibernate:hibernate:3.1");

    assertThat(artifacts.get(7).configurationName()).isEqualTo("releaseImplementation");
    assertThat(artifacts.get(7).compactNotation()).isEqualTo("com.example.libs:lib1:2.0");

    assertThat(artifacts.get(8).configurationName()).isEqualTo("releaseImplementation");
    assertThat(artifacts.get(8).compactNotation()).isEqualTo("com.example.libs:lib2:1.0");

    assertThat(artifacts.get(9).configurationName()).isEqualTo("debugApi");
    assertThat(artifacts.get(9).compactNotation()).isEqualTo("com.example.libs:lib4:1.0");

    assertThat(artifacts.get(10).configurationName()).isEqualTo("api");
    assertThat(artifacts.get(10).compactNotation()).isEqualTo("com.example.libs:lib3:2.0");
  }

  @Test
  public void testInsertionOrder() throws IOException {
    writeToBuildFile(TestFile.INSERTION_ORDER);

    GradleBuildModel buildModel = getGradleBuildModel();

    buildModel.dependencies().addArtifact("api", "com.example.libs1:lib1:1.0");
    buildModel.dependencies().addArtifact("feature", "com.example.libs3:lib3:3.0");
    buildModel.dependencies().addArtifact("testCompile", "com.example.libs2:lib2:2.0");
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.INSERTION_ORDER_EXPECTED);

    List<ArtifactDependencyModel> artifacts = buildModel.dependencies().artifacts();
    assertThat(artifacts.get(0).configurationName()).isEqualTo("feature");
    assertThat(artifacts.get(1).configurationName()).isEqualTo("api");
    assertThat(artifacts.get(2).configurationName()).isEqualTo("implementation");
    assertThat(artifacts.get(3).configurationName()).isEqualTo("testCompile");
  }

  @Test
  public void testSetConfigurationToEmpty() throws IOException {
    writeToBuildFile(TestFile.SET_CONFIGURATION_TO_EMPTY);

    GradleBuildModel buildModel = getGradleBuildModel();
    assertThat(buildModel.dependencies().artifacts()).hasSize(1);
    buildModel.dependencies().artifacts().get(0).setConfigurationName("");

    applyChangesAndReparse(buildModel);
    // the change to an empty configuration name should cause the writer to refuse to change the
    // identifier, without throwing an exception.
    verifyFileContents(myBuildFile, TestFile.SET_CONFIGURATION_TO_EMPTY);
  }

  @Test
  public void testSetConfigurationToNonStandard() throws IOException {
    writeToBuildFile(TestFile.SET_CONFIGURATION_TO_NON_STANDARD);

    GradleBuildModel buildModel = getGradleBuildModel();
    List<ArtifactDependencyModel> artifacts = buildModel.dependencies().artifacts();

    assertThat(artifacts).hasSize(1);
    artifacts.get(0).setConfigurationName("customImplementation");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.SET_CONFIGURATION_TO_NON_STANDARD_EXPECTED);

    artifacts = buildModel.dependencies().artifacts();
    assertThat(artifacts).hasSize(1);
    assertThat(artifacts.get(0).configurationName()).isEqualTo("customImplementation");
  }

  public static class ExpectedArtifactDependency extends ArtifactDependencySpecImpl {
    @NotNull public String configurationName;

    public ExpectedArtifactDependency(@NotNull String configurationName,
                                      @NotNull String name,
                                      @Nullable String group,
                                      @Nullable String version) {
      super(name, group, version);
      this.configurationName = configurationName;
    }

    public void assertMatches(@NotNull ArtifactDependencyModel actual) {
      assertEquals("configurationName", configurationName, actual.configurationName());
      assertEquals("group", getGroup(), actual.group().toString());
      assertEquals("name", getName(), actual.name().forceString());
      assertEquals("version", getVersion(), actual.version().toString());
      assertEquals("classifier", getClassifier(), actual.classifier().toString());
      assertEquals("extension", getExtension(), actual.extension().toString());
    }

    public boolean matches(@NotNull ArtifactDependencyModel dependency) {
      return configurationName.equals(dependency.configurationName()) &&
             getName().equals(dependency.name().forceString()) &&
             Objects.equal(getGroup(), dependency.group().toString()) &&
             Objects.equal(getVersion(), dependency.version().toString()) &&
             Objects.equal(getClassifier(), dependency.classifier().toString()) &&
             Objects.equal(getExtension(), dependency.extension().toString());
    }
  }

  enum TestFile implements TestFileName {
    GET_ONLY_ARTIFACTS("getOnlyArtifacts"),
    PARSING_WITH_COMPACT_NOTATION("parsingWithCompactNotation"),
    PARSING_WITH_MAP_NOTATION("parsingWithMapNotation"),
    ADD_DEPENDENCY("addDependency"),
    ADD_DEPENDENCY_EXPECTED("addDependencyExpected"),
    ADD_DEPENDENCY_WITH_CONFIGURATION_CLOSURE("addDependencyWithConfigurationClosure"),
    ADD_DEPENDENCY_WITH_CONFIGURATION_CLOSURE_EXPECTED("addDependencyWithConfigurationClosureExpected"),
    SET_VERSION_ON_DEPENDENCY_WITH_COMPACT_NOTATION("setVersionOnDependencyWithCompactNotation"),
    SET_VERSION_ON_DEPENDENCY_WITH_COMPACT_NOTATION_EXPECTED("setVersionOnDependencyWithCompactNotationExpected"),
    SET_VERSION_ON_DEPENDENCY_WITH_MAP_NOTATION("setVersionOnDependencyWithMapNotation"),
    SET_DEPENDENCY_WITH_COMPACT_NOTATION("setDependencyWithCompactNotation"),
    SET_DEPENDENCY_WITH_COMPACT_NOTATION_EXPECTED("setDependencyWithCompactNotationExpected"),
    SET_VERSION_ON_DEPENDENCY_WITH_MAP_NOTATION_EXPECTED("setVersionOnDependencyWithMapNotationExpected"),
    PARSE_DEPENDENCIES_WITH_COMPACT_NOTATION_IN_SINGLE_LINE("parseDependenciesWithCompactNotationInSingleLine"),
    PARSE_DEPENDENCIES_WITH_COMPACT_NOTATION_IN_SINGLE_LINE_WITH_COMMENTS("parseDependenciesWithCompactNotationInSingleLineWithComments"),
    PARSE_DEPENDENCIES_WITH_MAP_NOTATION_USING_SINGLE_CONFIGURATION_NAME("parseDependenciesWithMapNotationUsingSingleConfigurationName"),
    PARSE_DEPENDENCIES_WITH_MAP_NOTATION_USING_SINGLE_CONFIGURATION_NAME_NO_PARENTHESES("parseDependenciesWithMapNotationUsingSingleConfigurationNameNoParentheses"),
    RESET("reset"),
    REMOVE_DEPENDENCY_WITH_COMPACT_NOTATION("removeDependencyWithCompactNotation"),
    REMOVE_DEPENDENCY_WITH_COMPACT_NOTATION_EXPECTED("removeDependencyWithCompactNotationExpected"),
    REMOVE_DEPENDENCY_WITH_COMPACT_NOTATION_AND_SINGLE_CONFIGURATION_NAME("removeDependencyWithCompactNotationAndSingleConfigurationName"),
    REMOVE_DEPENDENCY_WITH_MAP_NOTATION("removeDependencyWithMapNotation"),
    REMOVE_DEPENDENCY_WITH_MAP_NOTATION_EXPECTED("removeDependencyWithMapNotationExpected"),
    REMOVE_DEPENDENCY_WITH_MAP_NOTATION_AND_SINGLE_CONFIGURATION_NAME("removeDependencyWithMapNotationAndSingleConfigurationName"),
    REMOVE_WHEN_MULTIPLE("removeWhenMultiple"),
    CONTAINS("contains"),
    PARSE_COMPACT_NOTATION_WITH_VARIABLES("parseCompactNotationWithVariables"),
    PARSE_COMPACT_NOTATION_WITH_QUOTED_IDENTIFIER_VARIABLES("parseCompactNotationWithQuotedIdentifierVariables"),
    PARSE_MAP_NOTATION_WITH_VARIABLES("parseMapNotationWithVariables"),
    PARSE_COMPACT_NOTATION_CLOSURE_WITH_VARIABLES("parseCompactNotationClosureWithVariables"),
    PARSE_MAP_NOTATION_CLOSURE_WITH_VARIABLES("parseMapNotationClosureWithVariables"),
    NON_DEPENDENCY_CODE_IN_DEPENDENCIES_SECTION("nonDependencyCodeInDependenciesSection"),
    REPLACE_DEPENDENCY_BY_PSI_ELEMENT("replaceDependencyByPsiElement"),
    REPLACE_DEPENDENCY_BY_PSI_ELEMENT_EXPECTED("replaceDependencyByPsiElementExpected"),
    REPLACE_DEPENDENCY_BY_CHILD_ELEMENT("replaceDependencyByChildElement"),
    REPLACE_DEPENDENCY_BY_CHILD_ELEMENT_EXPECTED("replaceDependencyByChildElementExpected"),
    REPLACE_DEPENDENCY_FAILS_IF_PSI_ELEMENT_IS_NOT_FOUND("replaceDependencyFailsIfPsiElementIsNotFound"),
    REPLACE_DEPENDENCY_USING_MAP_NOTATION_WITH_COMPACT_NOTATION("replaceDependencyUsingMapNotationWithCompactNotation"),
    REPLACE_DEPENDENCY_USING_MAP_NOTATION_WITH_COMPACT_NOTATION_EXPECTED("replaceDependencyUsingMapNotationWithCompactNotationExpected"),
    REPLACE_DEPENDENCY_USING_MAP_NOTATION_ADDING_FIELDS("replaceDependencyUsingMapNotationAddingFields"),
    REPLACE_DEPENDENCY_USING_MAP_NOTATION_ADDING_FIELDS_EXPECTED("replaceDependencyUsingMapNotationAddingFieldsExpected"),
    REPLACE_DEPENDENCY_USING_MAP_NOTATION_DELETE_FIELDS("replaceDependencyUsingMapNotationDeleteFields"),
    REPLACE_DEPENDENCY_USING_MAP_NOTATION_DELETE_FIELDS_EXPECTED("replaceDependencyUsingMapNotationDeleteFieldsExpected"),
    REPLACE_DEPENDENCY_IN_ARGUMENT_LIST("replaceDependencyInArgumentList"),
    REPLACE_METHOD_DEPENDENCY_WITH_CLOSURE("replaceMethodDependencyWithClosure"),
    REPLACE_METHOD_DEPENDENCY_WITH_CLOSURE_EXPECTED("replaceMethodDependencyWithClosureExpected"),
    REPLACE_APPLICATION_DEPENDENCIES("replaceApplicationDependencies"),
    REPLACE_APPLICATION_DEPENDENCIES_EXPECTED("replaceApplicationDependenciesExpected"),
    DELETE_GROUP_AND_VERSION("deleteGroupAndVersion"),
    DELETE_GROUP_AND_VERSION_EXPECTED("deleteGroupAndVersionExpected"),
    DELETE_NAME_AND_RENAME_UNSUPPORTED("deleteNameAndRenameUnsupported"),
    DELETE_IN_METHOD_CALL_WITH_PROPERTIES("deleteInMethodCallWithProperties"),
    DELETE_IN_METHOD_CALL_WITH_PROPERTIES_EXPECTED("deleteInMethodCallWithPropertiesExpected"),
    MISSING_PROPERTIES_COMPACT("missingPropertiesCompact"),
    MISSING_PROPERTIES_MAP("missingPropertiesMap"),
    COMPACT_NOTATION_PSI_ELEMENT("compactNotationPsiElement"),
    MULTIPLE_COMPACT_NOTATION_PSI_ELEMENTS("multipleCompactNotationPsiElements"),
    METHOD_CALL_COMPACT_PSI_ELEMENT("methodCallCompactPsiElement"),
    METHOD_CALL_MULTIPLE_COMPACT_PSI_ELEMENT("methodCallMultipleCompactPsiElement"),
    MAP_NOTATION_PSI_ELEMENT("mapNotationPsiElement"),
    COMPACT_NOTATION_SET_TO_REFERENCE("compactNotationSetToReference"),
    COMPACT_NOTATION_SET_TO_REFERENCE_EXPECTED("compactNotationSetToReferenceExpected"),
    COMPACT_NOTATION_ELEMENT_UNSUPPORTED_OPERATIONS("compactNotationElementUnsupportedOperations"),
    SET_I_STR_IN_COMPACT_NOTATION("setIStrInCompactNotation"),
    SET_I_STR_IN_COMPACT_NOTATION_EXPECTED("setIStrInCompactNotationExpected"),
    PARSE_FULL_REFERENCES_COMPACT_APPLICATION("parseFullReferencesCompactApplication"),
    SET_SINGLE_REFERENCE_COMPACT_APPLICATION("setSingleReferenceCompactApplication"),
    SET_SINGLE_REFERENCE_COMPACT_METHOD("setSingleReferenceCompactMethod"),
    SET_FULL_REFERENCES_COMPACT_APPLICATION("setFullReferencesCompactApplication"),
    SET_FULL_REFERENCES_COMPACT_APPLICATION_EXPECTED("setFullReferencesCompactApplicationExpected"),
    SET_FULL_REFERENCE_COMPACT_METHOD("setFullReferenceCompactMethod"),
    SET_FULL_REFERENCE_COMPACT_METHOD_EXPECTED("setFullReferenceCompactMethodExpected"),
    PARSE_FULL_REFERENCE_MAP("parseFullReferenceMap"),
    SET_FULL_REFERENCE_MAP("setFullReferenceMap"),
    CORRECT_OBTAIN_RESULT_MODEL("correctObtainResultModel"),
    SET_THROUGH_MAP_REFERENCE("setThroughMapReference"),
    SET_THROUGH_MAP_REFERENCE_EXPECTED("setThroughMapReferenceExpected"),
    MALFORMED_FAKE_ARTIFACT_ELEMENT("malformedFakeArtifactElement"),
    COMPACT_SET_THROUGH_REFERENCES("compactSetThroughReferences"),
    COMPACT_SET_THROUGH_REFERENCES_EXPECTED("compactSetThroughReferencesExpected"),
    EMPTY_FAKE_ARTIFACT_ELEMENT("emptyFakeArtifactElement"),
    FOLLOW_MULTIPLE_REFERENCES("followMultipleReferences"),
    CONFIGURE_CLOSURE_NO_PARENS("configureClosureNoParens"),
    CONFIGURE_CLOSURE_WITH_PARENS("configureClosureWithParens"),
    CONFIGURE_CLOSURE_PARENS("configureClosureParens"),
    SET_CONFIGURATION_WHEN_SINGLE("setConfigurationWhenSingle"),
    SET_CONFIGURATION_WHEN_SINGLE_EXPECTED("setConfigurationWhenSingleExpected"),
    SET_CONFIGURATION_WHEN_MULTIPLE("setConfigurationWhenMultiple"),
    SET_CONFIGURATION_TO_EMPTY("setConfigurationToEmpty"),
    SET_CONFIGURATION_TO_NON_STANDARD("setConfigurationToNonStandard"),
    SET_CONFIGURATION_TO_NON_STANDARD_EXPECTED("setConfigurationToNonStandardExpected"),
    SET_VERSION_REFERENCE("setVersionReference"),
    SET_VERSION_REFERENCE_EXPECTED("setVersionReferenceExpected"),
    SET_EXCLUDES_BLOCK_TO_REFERENCES("setExcludesBlockToReferences"),
    SET_EXCLUDES_BLOCK_TO_REFERENCES_EXPECTED("setExcludesBlockToReferencesExpected"),
    ARTIFACT_NOTATION_EDGE_CASES("artifactNotationEdgeCases"),
    INSERTION_ORDER("insertionOrder"),
    INSERTION_ORDER_EXPECTED("insertionOrderExpected"),
    SET_FULL_REFERENCE_MAP_EXPECTED("setFullReferenceMapExpected"),
    ;

    @NotNull private @SystemDependent String path;
    TestFile(@NotNull @SystemDependent String path) {
      this.path = path;
    }

    @NotNull
    @Override
    public File toFile(@NotNull @SystemDependent String basePath, @NotNull String extension) {
      return TestFileName.super.toFile(basePath + "/artifactDependency/" + path, extension);
    }
  }
}
