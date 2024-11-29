// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.facet.FacetManager
import com.intellij.facet.impl.FacetUtil
import com.intellij.ide.IdeEventQueue
import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RepositoryLibraryType
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.config.CompilerSettings.Companion.DEFAULT_ADDITIONAL_ARGUMENTS
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.util.findLibrary
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.configuration.findApplicableConfigurator
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.idea.facet.getRuntimeLibraryVersion
import org.jetbrains.kotlin.idea.projectConfiguration.LibraryJarDescriptor
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.configureKotlinFacet
import org.jetbrains.kotlin.idea.test.runAll
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@RunWith(JUnit38ClassRunner::class)
class UpdateConfigurationQuickFixTest : BasePlatformTestCase() {
    fun testDisableInlineClasses() {
        configureRuntime("mockRuntime11")
        resetProjectSettings(LanguageVersion.KOTLIN_1_3)
        myFixture.configureByText("foo.kt", "inline class My(val n: Int)")

        assertEquals(LanguageFeature.State.ENABLED_WITH_WARNING, inlineClassesSupport)
        assertTrue(myFixture.availableIntentions.none { it.text == "Disable inline classes support in the project" })
    }

    fun testEnableInlineClasses() {
        configureRuntime("mockRuntime11")
        resetProjectSettings(LanguageVersion.KOTLIN_1_3)
        myFixture.configureByText("foo.kt", "inline class My(val n: Int)")

        assertEquals(LanguageFeature.State.ENABLED_WITH_WARNING, inlineClassesSupport)
        myFixture.launchAction(myFixture.findSingleIntention("Enable inline classes support in the project"))
        assertEquals(LanguageFeature.State.ENABLED, inlineClassesSupport)
    }

    fun testModuleLanguageVersion() {
        configureRuntime("mockRuntime11")
        resetProjectSettings(LanguageVersion.KOTLIN_1_0)
        myFixture.configureByText("foo.kt", "val x get() = 1")
        // create facet to trigger "Set module language version" instead of "Set project language version"
        configureKotlinFacet(module)

        assertEquals(LanguageVersion.KOTLIN_1_0, module.languageVersionSettings.languageVersion)
        myFixture.launchAction(myFixture.findSingleIntention("Set module language version to 1.1"))
        assertEquals(LanguageVersion.KOTLIN_1_1, module.languageVersionSettings.languageVersion)
    }

    fun testProjectLanguageVersion() {
        configureRuntime("mockRuntime11")
        resetProjectSettings(LanguageVersion.KOTLIN_1_0)
        myFixture.configureByText("foo.kt", "val x get() = 1")

        myFixture.launchAction(myFixture.findSingleIntention("Set project language version to 1.1"))

        assertEquals("1.1", KotlinCommonCompilerArgumentsHolder.getInstance(project).settings.languageVersion)
        assertEquals("1.0", KotlinCommonCompilerArgumentsHolder.getInstance(project).settings.apiVersion)
    }

    fun testIncreaseLangAndApiLevel() {
        configureRuntime("mockRuntime11")
        resetProjectSettings(LanguageVersion.KOTLIN_1_0)
        myFixture.configureByText("foo.kt", "val x = <caret>\"s\"::length")

        myFixture.launchAction(myFixture.findSingleIntention("Set project language version to 1.1"))

        assertEquals("1.1", KotlinCommonCompilerArgumentsHolder.getInstance(project).settings.languageVersion)
        assertEquals("1.1", KotlinCommonCompilerArgumentsHolder.getInstance(project).settings.apiVersion)
    }

    fun testIncreaseLangAndApiLevel_10() {
        configureRuntime("mockRuntime106")
        resetProjectSettings(LanguageVersion.KOTLIN_1_0)
        myFixture.configureByText("foo.kt", "val x = <caret>\"s\"::length")

        myFixture.launchAction(myFixture.findSingleIntention("Set project language version to 1.1"))

        assertEquals("1.1", KotlinCommonCompilerArgumentsHolder.getInstance(project).settings.languageVersion)
        assertEquals("1.1", KotlinCommonCompilerArgumentsHolder.getInstance(project).settings.apiVersion)

        val actualVersion = getRuntimeLibraryVersion(myFixture.module)?.kotlinVersion
        assertEquals(KotlinPluginLayout.standaloneCompilerVersion.kotlinVersion, actualVersion)
    }

    fun testIncreaseLangLevelFacet_10() {
        configureRuntime("mockRuntime106")
        resetProjectSettings(LanguageVersion.KOTLIN_1_0)
        configureKotlinFacet(module) {
            settings.languageLevel = LanguageVersion.KOTLIN_1_0
            settings.apiLevel = LanguageVersion.KOTLIN_1_0
        }
        myFixture.configureByText("foo.kt", "val x = <caret>\"s\"::length")

        assertEquals(LanguageVersion.KOTLIN_1_0, module.languageVersionSettings.languageVersion)
        myFixture.launchAction(myFixture.findSingleIntention("Set module language version to 1.1"))
        assertEquals(LanguageVersion.KOTLIN_1_1, module.languageVersionSettings.languageVersion)

        val actualVersion = getRuntimeLibraryVersion(myFixture.module)
        assertEquals(KotlinPluginLayout.standaloneCompilerVersion.artifactVersion, actualVersion?.artifactVersion)
    }

    fun testAddKotlinReflect() {
        // The configurator uses a stable Kotlin version which has kotlin-reflect available on maven-central
        val configurator = findApplicableConfigurator(project.modules.first())
        configurator.configure(project, emptyList())
        myFixture.configureByText(
            "foo.kt", """class Foo(val prop: Any) {
                fun func() {}
            }

            fun y01() = Foo::prop.gett<caret>er
            """
        )
        myFixture.launchAction(myFixture.findSingleIntention("Add 'kotlin-reflect.jar' to the classpath"))
        var i = 0
        while (JarRepositoryManager.hasRunningTasks()) {
            Thread.sleep(100)
            if (i++ > 100) {
                error("Timeout error")
            }
        }
        IdeEventQueue.getInstance().flushQueue()
        val kotlinRuntime = module.findLibrary { LibraryJarDescriptor.REFLECT_JAR.findExistingJar(it) != null }
        assertNotNull(kotlinRuntime)
        val sources = kotlinRuntime!!.getFiles(OrderRootType.SOURCES)
        assertContainsElements(
            sources.map { it.name },
            "kotlin-reflect-${KotlinPluginLayout.standaloneCompilerVersion.artifactVersion}-sources.jar"
        )
    }

    private fun configureRuntime(path: String) {
        val name = if (path == "mockRuntime106") "kotlin-runtime.jar" else "kotlin-stdlib.jar"
        val sourcePath = when (path) {
            "actualRuntime" -> TestKotlinArtifacts.kotlinStdlib
            else -> File(IDEA_TEST_DATA_DIR, "configuration/$path/$name")
        }

        val tempFile = File(FileUtil.createTempDirectory("kotlin-update-configuration", null), name)
        FileUtil.copy(sourcePath, tempFile)
        val tempVFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile) ?: error("Can't find file: $tempFile")

        ConfigLibraryUtil.addLibrary(module, "KotlinJavaRuntime") {
            (this as LibraryEx.ModifiableModelEx).kind = RepositoryLibraryType.REPOSITORY_LIBRARY_KIND
            this.properties = LibraryJarDescriptor.RUNTIME_JDK8_JAR.repositoryLibraryProperties

            val jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(tempVFile) ?: error("Root not found for $tempVFile")
            addRoot(jarRoot, OrderRootType.CLASSES)
        }
    }

    private fun resetProjectSettings(version: LanguageVersion) {
        KotlinCompilerSettings.getInstance(project).update {
            additionalArguments = DEFAULT_ADDITIONAL_ARGUMENTS
        }
        KotlinCommonCompilerArgumentsHolder.getInstance(project).update {
            languageVersion = version.versionString
            apiVersion = version.versionString
        }
    }

    private val inlineClassesSupport: LanguageFeature.State
        get() = project.languageVersionSettings.getFeatureSupport(LanguageFeature.InlineClasses)

    override fun tearDown() {
        runAll(
            ThrowableRunnable { resetProjectSettings(KotlinPluginLayout.standaloneCompilerVersion.languageVersion) },
            ThrowableRunnable {
                FacetManager.getInstance(module).getFacetByType(KotlinFacetType.TYPE_ID)?.let {
                    FacetUtil.deleteFacet(it)
                }
            },
            ThrowableRunnable {
                OrderEnumerator.orderEntries(module).forEachLibrary {
                    ConfigLibraryUtil.removeLibrary(module, it.name!!)
                }
            },
            ThrowableRunnable { JarFileSystemImpl.cleanupForNextTest() },
            ThrowableRunnable { super.tearDown() }
        )
    }
}
