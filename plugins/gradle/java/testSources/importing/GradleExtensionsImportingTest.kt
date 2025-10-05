// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

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
    val expectedExtensionMap = buildMap {
      put("ext", "org.gradle.api.internal.plugins.DefaultExtraPropertiesExtension")
      put("idea", "org.gradle.plugins.ide.idea.model.IdeaModel")
      if (isGradleOlderThan("9.0")) {
        // See gradle/pull/32742
        put("defaultArtifacts", "org.gradle.api.internal.plugins.DefaultArtifactPublicationSet")
      }
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
    }

    Assertions.assertThat(actualExtensionMap)
      .containsExactlyInAnyOrderEntriesOf(expectedExtensionMap)
  }
}
