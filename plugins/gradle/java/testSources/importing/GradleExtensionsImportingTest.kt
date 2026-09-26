// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsUnordered
import org.assertj.core.api.Assertions
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings
import org.junit.Test

/**
 * @author Vladislav.Soroka
 */
class GradleExtensionsImportingTest : GradleImportingTestCase() {

  @Test
  fun testJavaProject() {
    importProject("apply plugin: 'java'\n" +
                  "apply plugin: 'idea'")

    assertModules("project", "project.main", "project.test")

    val extensions = GradleExtensionsSettings.getInstance(myProject).getExtensionsFor(getModule("project"))!!

    if (isGradleOlderThan("8.2")) {
      val actualConventionMap = extensions.conventions.associate { it.name to it.typeFqn }
      val expectedConventionMap = buildMap {
        put("base", "org.gradle.api.plugins.BasePluginConvention")
        put("java", "org.gradle.api.plugins.JavaPluginConvention")
      }
      Assertions.assertThat(actualConventionMap)
        .containsExactlyInAnyOrderEntriesOf(expectedConventionMap)
    }

    val actualExtensionMap = extensions.extensions.mapValues { entry -> entry.value.typeFqn }
    Assertions.assertThat(actualExtensionMap)
      .containsExactlyInAnyOrderEntriesOf(getExpectedExtensionsMap())

    verifyDependencyConfigurations(extensions)
  }

  private fun getExpectedExtensionsMap(): Map<String, String> = buildMap {
    put("ext", "org.gradle.api.internal.plugins.DefaultExtraPropertiesExtension")
    put("idea", "org.gradle.plugins.ide.idea.model.IdeaModel")
    put("reporting", "org.gradle.api.reporting.ReportingExtension")
    if (isGradleAtLeast("4.10")) {
      put("sourceSets", "org.gradle.api.tasks.SourceSetContainer")
      put("java", "org.gradle.api.plugins.internal.DefaultJavaPluginExtension")
    }
    if (isGradleAtLeast("5.2")) {
      // Replaced ext Gradle extension
      remove("ext", "org.gradle.api.internal.plugins.DefaultExtraPropertiesExtension")
      put("ext", "org.gradle.internal.extensibility.DefaultExtraPropertiesExtension")
    }
    if (isGradleAtLeast("6.2")) {
      put("javaInstalls", "org.gradle.jvm.toolchain.internal.DefaultJavaInstallationRegistry")
    }
    if (isGradleAtLeast("6.8")) {
      put("javaToolchains", "org.gradle.jvm.toolchain.internal.DefaultJavaToolchainService")
    }
    if (isGradleAtLeast("7.0")) {
      // Removed javaInstalls Gradle extension
      remove("javaInstalls", "org.gradle.jvm.toolchain.internal.DefaultJavaInstallationRegistry")
    }
    if (isGradleAtLeast("7.1")) {
      put("base", "org.gradle.api.plugins.internal.DefaultBasePluginExtension")
    }
    if (isGradleAtLeast("7.4")) {
      put("testing", "org.gradle.testing.base.internal.DefaultTestingExtension")
    }
    if (isGradleAtLeast("8.5")) {
      put("versionCatalogs", "org.gradle.api.internal.catalog.DefaultDependenciesAccessors\$DefaultVersionCatalogsExtension")
    }
    if (isGradleOlderThan("9.0")) {
      // See gradle/pull/32742
      put("defaultArtifacts", "org.gradle.api.internal.plugins.DefaultArtifactPublicationSet")
    }
    if (isGradleAtLeast("9.2")) {
      // https://github.com/gradle/gradle/pull/34831
      // org.gradle.testing.base.internal.DefaultTestingExtension was replaced by org.gradle.testing.base.TestingExtension
      put("testing", "org.gradle.testing.base.TestingExtension")
    }
  if (isGradleAtLeast("9.5.0")) {
        remove("base", "org.gradle.api.plugins.internal.DefaultBasePluginExtension")
        put("base", "org.gradle.api.plugins.BasePluginExtension")
      }
    }

  private fun verifyDependencyConfigurations(extensions: GradleExtensionsSettings.GradleExtensionsData) {
    val scopes = listOf("compileOnly", "testCompileOnly", "implementation", "testImplementation", "runtimeOnly", "testRuntimeOnly")
    val annotationProcessors = listOf("annotationProcessor", "testAnnotationProcessor")

    val expectedConfigurations = if (isGradleAtLeast("8.2"))
      scopes + annotationProcessors
    else
      // For Gradle < 8.2, it's unclear whether a configuration could declare dependencies or not.
      emptyList()

    val actualDependencyConfigurations = extensions.configurations.values
      .filter { it.canDeclareDependencies == true }
      .map { it.name }
    assertEqualsUnordered(expectedConfigurations, actualDependencyConfigurations) {
      "The list of configurations received from Gradle at sync that are dependency scopes should match the expected list"
    }
  }
}
