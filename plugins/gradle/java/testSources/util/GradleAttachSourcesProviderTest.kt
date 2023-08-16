// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import junit.framework.AssertionFailedError
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.DefaultExternalDependencyId
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.testFramework.util.importProject
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import java.util.function.Consumer

class GradleAttachSourcesProviderTest : GradleImportingTestCase() {

  @Test
  fun `test sources artifact notation parsing`() {
    val dependencyId = DefaultExternalDependencyId("mygroup", "myartifact", "myversion")
    assertEquals("mygroup:myartifact:myversion:sources",
                 GradleAttachSourcesProvider.getSourcesArtifactNotation(dependencyId.presentableName) {
                   true
                 })

    dependencyId.classifier = "myclassifier"
    assertEquals("mygroup:myartifact:myversion:sources",
                 GradleAttachSourcesProvider.getSourcesArtifactNotation(dependencyId.presentableName) {
                   true
                 })

    dependencyId.packaging = "mypackaging"
    assertEquals("mygroup:myartifact:myversion:sources",
                 GradleAttachSourcesProvider.getSourcesArtifactNotation(dependencyId.presentableName) {
                   true
                 })

    assertEquals("mygroup:myartifact:myversion:sources",
                 GradleAttachSourcesProvider.getSourcesArtifactNotation(DefaultExternalDependencyId("mygroup", "myartifact",
                                                                                                    "myversion")
                                                                          .apply { packaging = "mypackaging" }.presentableName) {
                   true
                 })

    assertEquals("myartifact:myversion:sources",
                 GradleAttachSourcesProvider.getSourcesArtifactNotation("myartifact:myversion") {
                   throw AssertionFailedError("artifactIdChecker shouldn't be called")
                 })

    assertEquals("mygroup:myartifact:sources",
                 GradleAttachSourcesProvider.getSourcesArtifactNotation("mygroup:myartifact") {
                   throw AssertionFailedError("artifactIdChecker shouldn't be called")
                 })

    assertEquals("mygroup:myartifact:myversion:sources",
                 GradleAttachSourcesProvider.getSourcesArtifactNotation("mygroup:myartifact:myversion@aar") {
                   throw AssertionFailedError("artifactIdChecker shouldn't be called")
                 })
  }

  @Test
  fun `test download sources dynamic task`() {
    val dependency = "junit:junit:4.12"
    val dependencyName = "Gradle: junit:junit:4.12"
    val dependencyJar = "junit-4.12.jar"
    val dependencySourcesJar = "junit-4.12-sources.jar"
    val classFromDependency = "junit.framework.Test"

    importProject {
      withJavaPlugin()
      withIdeaPlugin()
      withMavenCentral()
      addTestImplementationDependency(dependency)
      addPrefix("idea.module.downloadSources = false")
    }
    assertModules("project", "project.main", "project.test")
    assertSourcesDownloadedAndAttached(dependencyName = dependencyName,
                                       dependencyJar = dependencyJar,
                                       dependencySourcesJar = dependencySourcesJar,
                                       classFromDependency = classFromDependency,
                                       targetModule = "project.test"
    )
  }

  @Test
  @TargetVersions("6.5+")
  fun `test download sources with configuration cache`() {
    val dependency = "junit:junit:4.12"
    val dependencyName = "Gradle: junit:junit:4.12"
    val dependencyJar = "junit-4.12.jar"
    val dependencySourcesJar = "junit-4.12-sources.jar"
    val classFromDependency = "junit.framework.Test"

    createProjectSubFile("gradle.properties", "org.gradle.configuration-cache=true\n org.gradle.unsafe.configuration-cache=true")
    importProject {
      withJavaPlugin()
      withIdeaPlugin()
      addPrefix("idea.module.downloadSources = false")
      withMavenCentral()
      addTestImplementationDependency(dependency)
    }
    assertModules("project", "project.main", "project.test")
    assertSourcesDownloadedAndAttached(dependencyName = dependencyName,
                                       dependencyJar = dependencyJar,
                                       dependencySourcesJar = dependencySourcesJar,
                                       classFromDependency = classFromDependency,
                                       targetModule = "project.test"
    )
  }

  @Test
  @TargetVersions("!4.0")
  fun `test download sources from gradle sub module repository`() {
    val dependency = "junit:junit:4.12"
    val dependencyName = "Gradle: junit:junit:4.12"
    val dependencyJar = "junit-4.12.jar"
    val dependencySourcesJar = "junit-4.12-sources.jar"
    val classFromDependency = "junit.framework.Test"

    createSettingsFile("include 'projectA', 'projectB' ")
    importProject(
      createBuildScriptBuilder()
        .project(":projectA") { it: TestGradleBuildScriptBuilder ->
          it
            .withJavaPlugin()
            .withIdeaPlugin()
            .addPrefix("idea.module.downloadSources = false")
            .withMavenCentral()
            .addTestImplementationDependency(dependency)
        }
        .project(":projectB") { it: TestGradleBuildScriptBuilder ->
          it
            .withJavaPlugin()
        }
        .generate()
    )
    assertModules(
      "project",
      "project.projectA", "project.projectA.main", "project.projectA.test",
      "project.projectB", "project.projectB.main", "project.projectB.test",
    )
    assertSourcesDownloadedAndAttached(dependencyName = dependencyName,
                                       dependencyJar = dependencyJar,
                                       dependencySourcesJar = dependencySourcesJar,
                                       classFromDependency = classFromDependency,
                                       targetModule = "project.projectA.test"
    )
    assertThat(getModuleLibDeps("project.projectB.test", dependencyName)).isEmpty()
  }

  private fun assertSourcesDownloadedAndAttached(dependencyName: String,
                                                 dependencyJar: String,
                                                 dependencySourcesJar: String,
                                                 classFromDependency: String,
                                                 targetModule: String
  ) {
    val library: LibraryOrderEntry = getModuleLibDeps(targetModule, dependencyName).single()
    assertThat(library.getRootFiles(OrderRootType.CLASSES))
      .hasSize(1)
      .allSatisfy(Consumer { assertEquals(dependencyJar, it.name) })

    val psiFile = runReadAction {
      JavaPsiFacade.getInstance(myProject).findClass(classFromDependency, GlobalSearchScope.allScope(myProject))!!.containingFile
    }

    val output = mutableListOf<String>()
    val listener = object : ExternalSystemTaskNotificationListenerAdapter() {
      override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
        output += text
      }
    }
    try {
      ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(listener)
      val callback = GradleAttachSourcesProvider().getActions(mutableListOf(library), psiFile)
        .single()
        .perform(mutableListOf(library))
        .apply { waitFor(5000) }
      assertNull(callback.error)
    }
    finally {
      ExternalSystemProgressNotificationManager.getInstance().removeNotificationListener(listener)
    }

    assertThat(output)
      .filteredOn { it.startsWith("Sources were downloaded to") }
      .hasSize(1)
      .allSatisfy(Consumer { assertThat(it).endsWith(dependencySourcesJar) })

    assertThat(library.getRootFiles(OrderRootType.SOURCES))
      .hasSize(1)
      .allSatisfy(Consumer { assertEquals(dependencySourcesJar, it.name) })
  }
}