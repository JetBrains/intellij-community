// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.artifacts

import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.StandardProgressIndicatorBase
import com.intellij.openapi.util.Computable
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinArtifactsDownloader
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout

class KotlinLayoutTest : UsefulTestCase() {
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
        } finally {
            super.tearDown()
        }
    }

    fun `test hardcoded bundled kotlin jps plugin classpath is correct`() {
        val expectedClasspath = ProgressManager.getInstance().runProcess(
            Computable {
                KotlinArtifactsDownloader.downloadMavenArtifacts(
                    KotlinArtifacts.OLD_FAT_JAR_KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID,
                    KotlinPluginLayout.instance.standaloneCompilerVersion.rawVersion,
                    myFixture.project,
                    ProgressManager.getInstance().progressIndicator,
                    additionalMavenRepos = listOf(
                        RemoteRepositoryDescription(
                            "kotlin.ide.plugin.dependencies",
                            "Kotlin IDE Plugin Dependencies",
                            "https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies"
                        )
                    )
                )
            }, StandardProgressIndicatorBase()
        ).sorted()
        TestCase.assertTrue(expectedClasspath.isNotEmpty())
        val actualClasspath = KotlinPluginLayout.KOTLIN_JPS_PLUGIN_CLASSPATH.map { (_, artifactId) -> artifactId }.sorted()
        val message = "expected=\n${expectedClasspath.joinToString("\n")}\n\nactual=${actualClasspath.joinToString("\n")}"
        TestCase.assertEquals(message, expectedClasspath.size, actualClasspath.size)
        TestCase.assertTrue(message, expectedClasspath.zip(actualClasspath).all { (exp, act) -> exp.name.startsWith(act) })
    }
}
