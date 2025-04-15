// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.externalSystem.MavenRepositoryData
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaProject
import org.jetbrains.plugins.gradle.model.MavenRepositoryModel
import org.jetbrains.plugins.gradle.model.RepositoryModels
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class MavenRepositoriesProjectResolverTest {

  private val myRepoList: MutableList<MavenRepositoryModel> = arrayListOf()
  private lateinit var myResolver: MavenRepositoriesProjectResolver
  private lateinit var myProjectNode: DataNode<ProjectData>
  private lateinit var myModuleNode: DataNode<ModuleData>
  private lateinit var myProject: IdeaProject
  private lateinit var myModule: IdeaModule

  @Before
  fun setUp() {
    myResolver = MavenRepositoriesProjectResolver()
    myResolver.nextResolver = mock(GradleProjectResolverExtension::class.java)

    myRepoList.clear()
    val fakeModel = TestRepositoryModels(myRepoList)

    myProject = mock(IdeaProject::class.java)
    myModule = mock(IdeaModule::class.java)
    val projectData = ProjectData(GradleConstants.SYSTEM_ID,
                                  "testName",
                                  "fake/path",
                                  "fake/external/project/path")
    myProjectNode = DataNode(ProjectKeys.PROJECT, projectData, null)
    val moduleData = ModuleData("fakeId", GradleConstants.SYSTEM_ID, "typeId",
                                "moduleName", "fake/Path", "fake/Path")
    myModuleNode = DataNode(ProjectKeys.MODULE, moduleData, myProjectNode)

    val fakeContext = mock(ProjectResolverContext::class.java)
    `when`<RepositoryModels>(fakeContext.getRootModel(RepositoryModels::class.java)).thenReturn(fakeModel)
    `when`<RepositoryModels>(fakeContext.getExtraProject(myModule, RepositoryModels::class.java)).thenReturn(fakeModel)
    myResolver.setProjectResolverContext(fakeContext)
  }

  @Test
  fun testProjectRepositoriesImported() {
    val mavenRepo = MyMavenRepoModel("name", "http://some.host")
    myRepoList.add(mavenRepo)

    myResolver.populateProjectExtraModels(myProject, myProjectNode)

    assertProjectContainsExactly(mavenRepo)
  }

  @Test
  fun testModuleRepositoriesImported() {
    val mavenRepo = MyMavenRepoModel("name", "http://some.host")
    myRepoList.add(mavenRepo)

    myResolver.populateModuleExtraModels(myModule, myModuleNode)

    assertProjectContainsExactly(mavenRepo)
  }

  @Test
  fun testRepositoriesDeduplicated() {
    val mavenRepo1 = MyMavenRepoModel("name", "http://some.host")
    myRepoList.add(mavenRepo1)
    myRepoList.add(mavenRepo1)
    myRepoList.add(mavenRepo1)

    myResolver.populateProjectExtraModels(myProject, myProjectNode)
    myResolver.resolveFinished(myProjectNode)

    val mavenRepo2 = MyMavenRepoModel("name1", "http://some.other.host")
    myRepoList.apply {
      add(MyMavenRepoModel("name", "http://some.host"))
      add(MyMavenRepoModel("name", "http://some.host"))
      add(MyMavenRepoModel("name", "http://some.host"))
      add(mavenRepo2)
    }

    myResolver.populateModuleExtraModels(myModule, myModuleNode)
    myResolver.resolveFinished(myProjectNode)

    assertProjectContainsExactly(mavenRepo1, mavenRepo2)
  }

  private fun assertProjectContainsExactly(vararg expectedMavenRepoModels: MavenRepositoryModel) {
    assertEquals(expectedMavenRepoModels.toMavenRepoData(), myProjectNode.mavenRepositories())
  }

  private fun DataNode<*>.mavenRepositories(): Collection<MavenRepositoryData> =
    ExternalSystemApiUtil.getChildren(this, MavenRepositoryData.KEY).map { it.data }

  private fun Array<out MavenRepositoryModel>.toMavenRepoData(): Collection<MavenRepositoryData> =
    this.map { MavenRepositoryData(GradleConstants.SYSTEM_ID, it.name, it.url) }


  private class MyMavenRepoModel(private val myName: String, private val myUrl: String) : MavenRepositoryModel {
    override fun getName(): String = myName
    override fun getUrl(): String = myUrl
  }

  private class TestRepositoryModels(private val myRepositories: List<MavenRepositoryModel>) : RepositoryModels {
    override fun getRepositories(): List<MavenRepositoryModel> = myRepositories
  }
}