// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea

import com.intellij.facet.FacetManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.*
import com.intellij.testFramework.fixtures.*
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.workspaceModel.KotlinFacetBridgeFactory
import org.junit.Assume
import java.io.File

abstract class KotlinFacetTestCase : UsefulTestCase() {
    private lateinit var myTestFixture: JavaCodeInsightTestFixture
    lateinit var myProject: Project
    private lateinit var myKotlinFixtureBuilder: KotlinModuleFixtureBuilder

    val myModule: Module
        get() = myTestFixture.module

    private fun getTestDataPath(): String {
        return PathManagerEx.getTestDataPath().replace(File.separatorChar, '/') /*+ getBasePath()*/
    }


    override fun setUp() {
        super.setUp()

        val projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name)
        val testDataPath: String = getTestDataPath()

        myTestFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.fixture)
            ?: error("Failed to create test fixture")
        myTestFixture.testDataPath = testDataPath

        configureProjectBuilder(projectBuilder)
        myTestFixture.setUp()

        myProject = myTestFixture.project
        Assume.assumeTrue("Execute only if kotlin facet bridge enabled", KotlinFacetBridgeFactory.kotlinFacetBridgeEnabled)
    }

    fun getKotlinFacet(): KotlinFacet {
        val facetManager = FacetManager.getInstance(myModule)
        assertSize(1, facetManager.allFacets)
        return facetManager.allFacets[0] as KotlinFacet
    }

    fun fireFacetChangedEvent(mainFacet: KotlinFacet) {
        val allFacets = FacetManager.getInstance(myModule).allFacets
        assertSize(1, allFacets)
        assertSame(mainFacet, allFacets[0])

        allFacets.forEach { facet -> FacetManager.getInstance(myModule).facetConfigurationChanged(facet) }
    }

    protected open fun configureProjectBuilder(projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture?>) {
        val tempDirPath: String = myTestFixture.tempDirPath
        IdeaTestFixtureFactory.getFixtureFactory().registerFixtureBuilder(
            KotlinModuleFixtureBuilder::class.java,
            KotlinModuleFixtureBuilderImpl::class.java.name
        )
        myKotlinFixtureBuilder = projectBuilder.addModule(KotlinModuleFixtureBuilder::class.java)
        myKotlinFixtureBuilder.addContentRoot(tempDirPath)
    }

    override fun tearDown() {
        val fixture: JavaCodeInsightTestFixture = myTestFixture
        try {
            fixture.tearDown()
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }
}

