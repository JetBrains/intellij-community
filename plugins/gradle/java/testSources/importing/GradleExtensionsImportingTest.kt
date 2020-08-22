// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.externalSystem.test.ExternalSystemTestUtil.assertMapsEqual
import org.gradle.util.GradleVersion
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

    val extensions = GradleExtensionsSettings.getInstance(myProject).getExtensionsFor(getModule("project"))

    val conventionsMap = extensions!!.conventions.map { it.name to it.typeFqn }.toMap()
    assertMapsEqual(mapOf("base" to "org.gradle.api.plugins.BasePluginConvention",
                          "java" to "org.gradle.api.plugins.JavaPluginConvention"),
                    conventionsMap)

    val extensionsMap = extensions.extensions.mapValues { entry -> entry.value.typeFqn }


    val baseVer = GradleVersion.version(gradleVersion).baseVersion
    val expectedExtensions = when {
      baseVer <= GradleVersion.version("2.7") ->
        mapOf<String, String?>("ext" to extraPropertiesExtensionFqn,
                               "idea" to "org.gradle.plugins.ide.idea.model.IdeaModel",
                               "sources" to "org.gradle.language.base.internal.DefaultProjectSourceSet",
                               "binaries" to "org.gradle.platform.base.internal.DefaultBinaryContainer",
                               "defaultArtifacts" to "org.gradle.api.internal.plugins.DefaultArtifactPublicationSet",
                               "reporting" to "org.gradle.api.reporting.ReportingExtension")

      baseVer <= GradleVersion.version("2.8") ->
        mapOf<String, String?>("ext" to extraPropertiesExtensionFqn,
                               "idea" to "org.gradle.plugins.ide.idea.model.IdeaModel",
                               "binaries" to "org.gradle.platform.base.internal.DefaultBinaryContainer",
                               "defaultArtifacts" to "org.gradle.api.internal.plugins.DefaultArtifactPublicationSet",
                               "reporting" to "org.gradle.api.reporting.ReportingExtension")

      baseVer <= GradleVersion.version("4.9") ->
        mapOf<String, String?>("ext" to extraPropertiesExtensionFqn,
                               "idea" to "org.gradle.plugins.ide.idea.model.IdeaModel",
                               "defaultArtifacts" to "org.gradle.api.internal.plugins.DefaultArtifactPublicationSet",
                               "reporting" to "org.gradle.api.reporting.ReportingExtension")

      baseVer < GradleVersion.version("4.10.3") ->
        mapOf<String, String?>("ext" to extraPropertiesExtensionFqn,
                               "idea" to "org.gradle.plugins.ide.idea.model.IdeaModel",
                               "defaultArtifacts" to "org.gradle.api.internal.plugins.DefaultArtifactPublicationSet",
                               "reporting" to "org.gradle.api.reporting.ReportingExtension",
                               "sourceSets" to "org.gradle.api.internal.tasks.DefaultSourceSetContainer",
                               "java" to "org.gradle.api.plugins.internal.DefaultJavaPluginExtension")

      baseVer < GradleVersion.version("6.2") ->
        mapOf<String, String?>("ext" to extraPropertiesExtensionFqn,
                               "idea" to "org.gradle.plugins.ide.idea.model.IdeaModel",
                               "defaultArtifacts" to "org.gradle.api.internal.plugins.DefaultArtifactPublicationSet",
                               "reporting" to "org.gradle.api.reporting.ReportingExtension",
                               "sourceSets" to "org.gradle.api.tasks.SourceSetContainer",
                               "java" to "org.gradle.api.plugins.internal.DefaultJavaPluginExtension")

      else ->
        mapOf<String, String?>("ext" to extraPropertiesExtensionFqn,
                               "idea" to "org.gradle.plugins.ide.idea.model.IdeaModel",
                               "defaultArtifacts" to "org.gradle.api.internal.plugins.DefaultArtifactPublicationSet",
                               "reporting" to "org.gradle.api.reporting.ReportingExtension",
                               "sourceSets" to "org.gradle.api.tasks.SourceSetContainer",
                               "java" to "org.gradle.api.plugins.internal.DefaultJavaPluginExtension",
                               "javaInstalls" to "org.gradle.jvm.toolchain.internal.DefaultJavaInstallationRegistry")
    }

    assertMapsEqual(expectedExtensions, extensionsMap)
  }
}
