// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.data

import com.intellij.codeInsight.ExternalAnnotationsArtifactsResolver
import com.intellij.codeInsight.externalAnnotation.location.AnnotationsLocation
import com.intellij.codeInsight.externalAnnotation.location.AnnotationsLocationProvider
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.testFramework.LightIdeaTestCase
import com.intellij.testFramework.RunAll
import com.intellij.util.ThrowableRunnable
import org.assertj.core.api.BDDAssertions.then
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Test

class ExternalAnnotationsDataServiceTest: LightIdeaTestCase() {

  private lateinit var modelsProvider: IdeModifiableModelsProvider
  private lateinit var resolver: TestExternalAnnotationsResolver
  private lateinit var locationProvider: TestExternalAnnotationLocationProvider
  private lateinit var projectData: ProjectData

  override fun setUp() {
    super.setUp()
    modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(project)

    resolver = TestExternalAnnotationsResolver()
    locationProvider = TestExternalAnnotationLocationProvider()

    projectData = ProjectData(GradleConstants.SYSTEM_ID, "", "", "projectPath")
    GradleSettings.getInstance(project).linkedProjectsSettings = listOf(GradleProjectSettings().apply {
      isResolveExternalAnnotations = true
      externalProjectPath = projectData.linkedExternalProjectPath
    })

    Extensions.getRootArea().getExtensionPoint(ExternalAnnotationsArtifactsResolver.EP_NAME).registerExtension(resolver, testRootDisposable)
    Extensions.getRootArea().getExtensionPoint(AnnotationsLocationProvider.EP_NAME).registerExtension(locationProvider, testRootDisposable)
  }

  @Test
  fun `test invalid locations are skipped`() {
    val service = ExternalAnnotationsDataService()

    val libraryNames = listOf("library1", "library2", "library3")

    libraryNames.forEach { modelsProvider.createLibrary("Gradle: $it") }

    val libraryDataNodes = libraryNames.map {
      val libData = LibraryData(GradleConstants.SYSTEM_ID, it).apply {
        setGroup("grp")
        artifactId = it
        version = "1.0"
      }
      return@map DataNode(ProjectKeys.LIBRARY, libData, null)
    }.toMutableList()

    val location = AnnotationsLocation("grp", "annotation-artifact", "1.0")
    libraryNames.forEach { locationProvider.addLocation("grp", it, "1.0", location) }

    service.onSuccessImport(libraryDataNodes, projectData, project, modelsProvider)

    then(resolver.attemptsCount).describedAs("Broken annotations location should only be attempted once").isEqualTo(1)
  }

  override fun tearDown() {
    RunAll(
      ThrowableRunnable { modelsProvider.dispose() },
      ThrowableRunnable { GradleSettings.getInstance(project).unlinkExternalProject(projectData.linkedExternalProjectPath) },
      ThrowableRunnable { super.tearDown() }
    ).run()
  }
}

class TestExternalAnnotationsResolver : ExternalAnnotationsArtifactsResolver {
  var attemptsCount = 0
  override fun resolve(project: Project, library: Library, mavenId: String?): Boolean = false

  override fun resolve(project: Project, library: Library, annotationsLocation: AnnotationsLocation): Boolean {
    attemptsCount++
    return false
  }

  override fun resolveAsync(project: Project, library: Library, mavenId: String?): Promise<Library> = resolvedPromise(library)
}

class TestExternalAnnotationLocationProvider: AnnotationsLocationProvider {

  private val knownLocations = mutableMapOf<String, AnnotationsLocation>()

  override fun getLocations(project: Project,
                            library: Library,
                            artifactId: String?,
                            groupId: String?,
                            version: String?): MutableCollection<AnnotationsLocation> {
    val location = knownLocations["$groupId:$artifactId:$version"]
    return if (location == null) {
      mutableListOf()
    } else {
      mutableListOf(location)
    }
  }

  fun addLocation(groupId: String, artifactId: String, version: String, annotationsLocation: AnnotationsLocation) {
    knownLocations["$groupId:$artifactId:$version"] = annotationsLocation
  }
}
