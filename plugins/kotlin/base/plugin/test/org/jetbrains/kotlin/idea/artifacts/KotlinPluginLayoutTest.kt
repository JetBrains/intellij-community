// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.artifacts

import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.StandardProgressIndicatorBase
import com.intellij.openapi.util.Computable
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.OLD_FAT_JAR_KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinArtifactsDownloader
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import java.util.concurrent.Callable

class KotlinPluginLayoutTest : UsefulTestCase() {
    private lateinit var myFixture: IdeaProjectTestFixture

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        val projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name)
        myFixture = projectBuilder.fixture
        myFixture.setUp()
    }

    @Throws(Exception::class)
    override fun tearDown() {
        try {
            myFixture.tearDown()
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    fun `test hardcoded bundled kotlin jps plugin classpath is correct`() {
        val future = ApplicationManager.getApplication().executeOnPooledThread(Callable {
            ProgressManager.getInstance().runProcess(
              Computable {
                  KotlinArtifactsDownloader.downloadMavenArtifacts(
                    @Suppress("DEPRECATION") OLD_FAT_JAR_KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID,
                    KotlinPluginLayout.standaloneCompilerVersion.kotlinVersion.toString(),
                    myFixture.project,
                    ProgressManager.getInstance().progressIndicator,
                    additionalMavenRepos = listOf(
                      RemoteRepositoryDescription(
                        "kotlin.ide.plugin.dependencies",
                        "Kotlin IDE Plugin Dependencies",
                        "https://cache-redirector.jetbrains.com/intellij-dependencies"
                      )
                    )
                  )
              }, StandardProgressIndicatorBase()
            ).sorted()
            })
        while (!future.isDone) {
            UIUtil.dispatchAllInvocationEvents()
        }
        val remoteArtifactClasspath = future.get()
        assertEquals(1, remoteArtifactClasspath.size)
        assertTrue(remoteArtifactClasspath[0].name.startsWith(@Suppress("DEPRECATION") OLD_FAT_JAR_KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID))
    }
}
