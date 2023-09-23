// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.*
import java.io.File

abstract class KotlinFacetTestCase : UsefulTestCase() {
    protected lateinit var myTestFixture: JavaCodeInsightTestFixture
    lateinit var myProject: Project
    lateinit var myKotlinFixtureBuilder: KotlinModuleFixtureBuilder

    val myModule: Module
        get() = myTestFixture.module

    private fun getTestDataPath(): String {
        return PathManagerEx.getTestDataPath().replace(File.separatorChar, '/') /*+ getBasePath()*/
    }

    override fun setUp() {
        super.setUp()
        //TODO: remove after enabling by default
        Registry.get("workspace.model.kotlin.facet.bridge").setValue(true)
        val projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(
            name
        )

        val testDataPath: String = getTestDataPath()

        myTestFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.fixture)
        myTestFixture!!.testDataPath = testDataPath

        configureProjectBuilder(projectBuilder)
        //myFixture.setUp()
        myTestFixture!!.setUp()

        myProject = myTestFixture.project
    }

    protected open fun configureProjectBuilder(projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture?>) {
        val tempDirPath: String = myTestFixture.tempDirPath
        IdeaTestFixtureFactory.getFixtureFactory().registerFixtureBuilder(
            KotlinModuleFixtureBuilder::class.java,
            "KotlinModuleFixtureBuilderImpl"
        )
        myKotlinFixtureBuilder = projectBuilder.addModule(KotlinModuleFixtureBuilder::class.java)
        myKotlinFixtureBuilder.addContentRoot(tempDirPath)
        //configure(myKotlinFixtureBuilder)
        //TODO: add root here
    }

    override fun tearDown() {
        val fixture: JavaCodeInsightTestFixture = myTestFixture
        //myTestFixture = null
        try {
            fixture.tearDown()
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }
}

