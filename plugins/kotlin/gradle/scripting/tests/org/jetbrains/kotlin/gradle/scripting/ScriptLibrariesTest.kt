// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting

import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertion
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertion.Companion.assertCollectionOrdered
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsOrdered
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsUnordered
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.kotlin.gradle.scripting.importing.GradleKotlinDslScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.configurations.toVirtualFileUrl
import org.jetbrains.kotlin.idea.core.script.modules.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.modules.KotlinScriptLibraryEntityId
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.io.File

private const val PROJECT = "/home/dev/project"
private const val GRADLE_USER_HOME = "/home/dev/.gradle"
private const val GRADLE_DIST = "$GRADLE_USER_HOME/wrapper/dists/gradle-8.11-all/1a2b3c4d/gradle-8.11"
private const val MODULES_CACHE = "$GRADLE_USER_HOME/caches/modules-2/files-2.1"
private const val ACCESSORS_CACHE = "$GRADLE_USER_HOME/caches/8.11/kotlin-dsl/accessors"

private const val GUAVA_JAR = "$MODULES_CACHE/com.google.guava/guava/33.0.0-jre/8f2a1b/guava-33.0.0-jre.jar"
private const val GUAVA_SOURCES_JAR = "$MODULES_CACHE/com.google.guava/guava/33.0.0-jre/c3d4e5/guava-33.0.0-jre-sources.jar"
private const val COMMONS_IO_JAR = "$MODULES_CACHE/commons-io/commons-io/2.16.1/1f2e3d/commons-io-2.16.1.jar"
private const val COMMONS_IO_SOURCES_JAR = "$MODULES_CACHE/commons-io/commons-io/2.16.1/4c5b6a/commons-io-2.16.1-sources.jar"
private const val JSR305_JAR = "$MODULES_CACHE/com.google.code.findbugs/jsr305/3.0.2/7a6b5c/jsr305-3.0.2.jar"
private const val GRADLE_CORE_JAR = "$GRADLE_DIST/lib/gradle-core-8.11.jar"
private const val BUILD_LOGIC_JAR = "$PROJECT/build-logic/build/libs/build-logic.jar"
private const val KOTLIN_STDLIB_JAR = "$GRADLE_DIST/lib/kotlin-stdlib-2.0.20.jar"
private const val KOTLIN_STDLIB_SOURCES_JAR = "$MODULES_CACHE/org.jetbrains.kotlin/kotlin-stdlib/2.0.20/d6e7f8/kotlin-stdlib-2.0.20-sources.jar"

@TestApplication
class ScriptLibrariesTest {
    private val urlManager = VirtualFileUrlManagerImpl()
    private val storage = MutableEntityStorage.create()
    private val script = url("$PROJECT/build.gradle.kts")

    @Test
    fun `class jars are paired with the sources jars of the same name`() {
        val ids = libraries().register(
            script,
            files(GUAVA_JAR, COMMONS_IO_JAR, JSR305_JAR),
            files(COMMONS_IO_SOURCES_JAR, GUAVA_SOURCES_JAR),
        )

        assertCollectionOrdered(ids) {
            assertLibrary(classes = listOf(GUAVA_JAR), sources = setOf(GUAVA_SOURCES_JAR))
            assertLibrary(classes = listOf(COMMONS_IO_JAR), sources = setOf(COMMONS_IO_SOURCES_JAR))
            assertLibrary(classes = listOf(JSR305_JAR))
        }
    }

    @Test
    fun `sources jar of another version is not attached as sources`() {
        val otherSourcesJar = "$MODULES_CACHE/com.google.guava/guava/32.1.3-jre/9a8b7c/guava-32.1.3-jre-sources.jar"

        val ids = libraries().register(script, files(GUAVA_JAR), files(otherSourcesJar))

        assertCollectionOrdered(ids) {
            assertElement { id ->
                val library = storage.library(id)
                assertEqualsOrdered(listOf(url(GUAVA_JAR)), library.classes)
                assertFalse(url(otherSourcesJar) in library.sources)
            }
        }
    }

    @Test
    fun `source directories are attached by parent to every class root without a sources jar`() {
        val ids = libraries().register(
            script,
            files(GRADLE_CORE_JAR, BUILD_LOGIC_JAR, GUAVA_JAR),
            files(
                "$GRADLE_DIST/src/core",
                "$GRADLE_DIST/src/core-api",
                "$PROJECT/build-logic/src/main/kotlin",
                "$PROJECT/build-logic/src/main/java",
                GUAVA_SOURCES_JAR,
            ),
        )

        val parentDirectories = setOf("$GRADLE_DIST/src", "$PROJECT/build-logic/src/main")
        assertCollectionOrdered(ids) {
            assertLibrary(classes = listOf(GUAVA_JAR), sources = setOf(GUAVA_SOURCES_JAR))
            assertLibrary(classes = listOf(GRADLE_CORE_JAR), sources = parentDirectories)
            assertLibrary(classes = listOf(BUILD_LOGIC_JAR), sources = parentDirectories)
        }
    }

    @Test
    fun `kotlin dsl accessors directories form one library with their source directories`() {
        val ids = libraries().register(
            script,
            files("$ACCESSORS_CACHE/9f8e7d/classes", "$ACCESSORS_CACHE/6c5b4a/classes", GRADLE_CORE_JAR),
            files("$ACCESSORS_CACHE/9f8e7d/src", "$ACCESSORS_CACHE/6c5b4a/src", "$GRADLE_DIST/src/core"),
        )

        assertCollectionOrdered(ids) {
            assertLibrary(
                classes = listOf("$ACCESSORS_CACHE/6c5b4a/classes", "$ACCESSORS_CACHE/9f8e7d/classes"),
                sources = setOf("$ACCESSORS_CACHE/6c5b4a/src", "$ACCESSORS_CACHE/9f8e7d/src"),
            )
            assertLibrary(classes = listOf(GRADLE_CORE_JAR), sources = setOf("$GRADLE_DIST/src"))
        }
    }

    @Test
    fun `kotlin stdlib and kotlin gradle plugin roots are grouped by marker in a fixed order`() {
        val kgpJar = "$MODULES_CACHE/org.jetbrains.kotlin/kotlin-gradle-plugin/2.0.20/a1b2c3/kotlin-gradle-plugin-2.0.20.jar"
        val kgpApiJar = "$MODULES_CACHE/org.jetbrains.kotlin/kotlin-gradle-plugin-api/2.0.20/b2c3d4/kotlin-gradle-plugin-api-2.0.20.jar"
        val kgpSourcesJar = "$MODULES_CACHE/org.jetbrains.kotlin/kotlin-gradle-plugin/2.0.20/c3d4e5/kotlin-gradle-plugin-2.0.20-sources.jar"

        val ids = libraries().register(
            script,
            files(kgpJar, GRADLE_CORE_JAR, KOTLIN_STDLIB_JAR, kgpApiJar),
            files(KOTLIN_STDLIB_SOURCES_JAR, kgpSourcesJar),
        )

        assertCollectionOrdered(ids) {
            assertLibrary(classes = listOf(KOTLIN_STDLIB_JAR), sources = setOf(KOTLIN_STDLIB_SOURCES_JAR))
            assertLibrary(classes = listOf(kgpApiJar, kgpJar), sources = setOf(kgpSourcesJar))
            assertLibrary(classes = listOf(GRADLE_CORE_JAR))
        }
    }

    @Test
    fun `library used by two scripts merges usages and sources`() {
        val appScript = url("$PROJECT/app/build.gradle.kts")
        val libraries = libraries()

        val first = libraries.register(script, files(BUILD_LOGIC_JAR), files("$PROJECT/build-logic/src/main/kotlin"))
        val second = libraries.register(appScript, files(BUILD_LOGIC_JAR), files("$PROJECT/gradle/plugins/src/main/kotlin"))

        assertEquals(first, second)
        assertCollectionOrdered(second) {
            assertLibrary(
                classes = listOf(BUILD_LOGIC_JAR),
                sources = setOf("$PROJECT/build-logic/src/main", "$PROJECT/gradle/plugins/src/main"),
            )
        }
        assertEquals(setOf(script, appScript), storage.library(second.single()).usedInScripts)
    }

    @Test
    fun `equal roots of different gradle projects get separate libraries`() {
        val otherScript = url("/home/dev/other/build.gradle.kts")

        val first = libraries(projectPath = PROJECT).register(script, files(GUAVA_JAR), files(GUAVA_SOURCES_JAR))
        val second = libraries(projectPath = "/home/dev/other").register(otherScript, files(GUAVA_JAR), files())

        assertNotEquals(first, second)
        assertEquals(2, storage.entities(KotlinScriptLibraryEntity::class.java).count())
        assertCollectionOrdered(first) {
            assertLibrary(classes = listOf(GUAVA_JAR), sources = setOf(GUAVA_SOURCES_JAR))
        }
        assertCollectionOrdered(second) {
            assertLibrary(classes = listOf(GUAVA_JAR))
        }
        assertEquals(setOf(script), storage.library(first.single()).usedInScripts)
        assertEquals(setOf(otherScript), storage.library(second.single()).usedInScripts)
    }

    @Test
    fun `no sources are attached when sources are off`() {
        val ids = libraries(attachSources = false).register(
            script,
            files(GUAVA_JAR, GRADLE_CORE_JAR),
            files(GUAVA_SOURCES_JAR, "$GRADLE_DIST/src/core"),
        )

        assertCollectionOrdered(ids) {
            assertLibrary(classes = listOf(GUAVA_JAR))
            assertLibrary(classes = listOf(GRADLE_CORE_JAR))
        }
    }

    @Test
    fun `marker groups keep their sources when sources are off`() {
        val ids = libraries(attachSources = false).register(script, files(KOTLIN_STDLIB_JAR), files(KOTLIN_STDLIB_SOURCES_JAR))

        assertCollectionOrdered(ids) {
            assertLibrary(classes = listOf(KOTLIN_STDLIB_JAR), sources = setOf(KOTLIN_STDLIB_SOURCES_JAR))
        }
    }

    private fun CollectionAssertion<KotlinScriptLibraryEntityId>.assertLibrary(classes: List<String>, sources: Set<String> = emptySet()) {
        assertElement { id ->
            val library = storage.library(id)
            assertEqualsOrdered(classes.map(::url), library.classes)
            assertEqualsUnordered(sources.map(::url), library.sources)
        }
    }

    private fun libraries(attachSources: Boolean = true, projectPath: String = PROJECT): ScriptLibraries {
        val entitySource = GradleKotlinDslScriptEntitySource(projectPath, GradleSyncPhase.SCRIPT_MODEL_PHASE)
        return ScriptLibraries(storage, entitySource, urlManager, attachSources)
    }

    private fun files(vararg paths: String): List<File> = paths.map(::File)

    private fun url(path: String): VirtualFileUrl = File(path).path.toVirtualFileUrl(urlManager)

    private fun MutableEntityStorage.library(id: KotlinScriptLibraryEntityId): KotlinScriptLibraryEntity =
        requireNotNull(resolve(id)) { "No library entity for $id" }
}
