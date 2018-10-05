// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.data

import com.intellij.externalSystem.MavenRepositoryData
import com.intellij.jarRepository.RemoteRepositoriesConfiguration
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl
import com.intellij.testFramework.LightIdeaTestCase
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.After
import org.junit.Before
import org.junit.Test

class MavenRepositoriesDataServiceTest: LightIdeaTestCase() {

  private lateinit var myModelsProvider: IdeModifiableModelsProvider

  @Before
  override fun setUp() {
    super.setUp()
    myModelsProvider = IdeModifiableModelsProviderImpl(getProject())
  }

  @After
  override fun tearDown() {
    try {
      val repositoriesConfiguration = RemoteRepositoriesConfiguration.getInstance(getProject())
      repositoriesConfiguration.resetToDefault()
      myModelsProvider.dispose()
    } finally {
      super.tearDown()
    }
  }

  @Test
  fun testMavenRepositoriesDataApplied() {
    val service = MavenRepositoriesDataService()
    val imported = mutableSetOf(DataNode<MavenRepositoryData>(MavenRepositoryData.KEY,
                                                       MavenRepositoryData(GradleConstants.SYSTEM_ID, "repoName", "repoUrl"),
                                                       null))

    service.onSuccessImport(imported, null, getProject(), myModelsProvider)

    val repositories = RemoteRepositoriesConfiguration.getInstance(getProject()).repositories

    assertContainsElements(repositories, RemoteRepositoryDescription("repoName", "repoName", "repoUrl"))
  }
}