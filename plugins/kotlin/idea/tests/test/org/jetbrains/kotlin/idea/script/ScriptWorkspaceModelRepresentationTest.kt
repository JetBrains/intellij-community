// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.script

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.psi.PsiManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.util.io.createParentDirectories
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.`is`
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts.jetbrainsAnnotations
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts.kotlinScriptingCommon
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts.kotlinScriptingJvm
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts.kotlinTestJs
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.core.script.k1.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.k1.ucache.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k1.ucache.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.test.TestMetadataUtil.getTestRoot
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile

@TestRoot("idea/tests")
class ScriptWorkspaceModelRepresentationTest : HeavyPlatformTestCase() {

    private val scriptDefinitionSourcePath: String

    init {
        val testRoot = getTestRoot(this::class.java) ?: error("@TestRoot annotation is missing")
        scriptDefinitionSourcePath = File(testRoot, "test/org/jetbrains/kotlin/idea/script/definition").absolutePath
    }

    // ON FIRST IMPORT
    fun testThatRequiredEntitiesCreatedOnFirstSync() {
        setUpEnvironment(
            aKtsLibs = listOf(kotlinTestJs, jetbrainsAnnotations),
            bKtsLibs = listOf(kotlinTestJs)
        )

        val scriptAName = "script.a.kts"
        val scriptBName = "script.b.kts"

        val scriptAPath = addScript(scriptAName)
        val scriptBPath = addScript(scriptBName)

        val storage = WorkspaceModel.getInstance(project).currentSnapshot

        // Checking script entities

        val scriptEntities = storage.entities(KotlinScriptEntity::class.java).toList()

        scriptEntities.assertContainsOnly(
            "Model doesn't contain expected scripts",
            mapOf(
                "path" to scriptAPath,
                "dependencies" to listOf(
                    mapOf("name" to kotlinTestJs.path),
                    mapOf("name" to jetbrainsAnnotations.path)
                )
            ),
            mapOf(
                "path" to scriptBPath,
                "dependencies" to listOf(
                    mapOf("name" to kotlinTestJs.path)
                )
            )
        )

        val scriptAEntity = scriptEntities.single { it.path == scriptAPath }
        val scriptBEntity = scriptEntities.single { it.path == scriptBPath }

        // Checking libraries entities

        val libraryEntities = storage.entities(KotlinScriptLibraryEntity::class.java).toList()
        libraryEntities.assertContainsOnly(
            "Model doesn't contain expected library entities",
            mapOf(
                "usedInScripts" to listOf(
                    mapOf("path" to scriptAEntity.path),
                    mapOf("path" to scriptBEntity.path),
                ),
                "roots" to listOf(
                    mapOf("url" to (VirtualFileUrl::getPresentableUrl to kotlinTestJs.path))
                )
            ),
            mapOf(
                "usedInScripts" to listOf(
                    mapOf("path" to scriptAEntity.path),
                ),
                "roots" to listOf(
                    mapOf("url" to (VirtualFileUrl::getPresentableUrl to jetbrainsAnnotations.path))
                )
            )
        )
    }

    fun testThatRefsAreResolvableBothSides() {
        setUpEnvironment(
            aKtsLibs = listOf(kotlinTestJs, jetbrainsAnnotations),
            bKtsLibs = listOf(kotlinTestJs)
        )

        val scriptA = addAndLoadScript("script.a.kts")
        val scriptB = addAndLoadScript("script.b.kts")

        val storage = WorkspaceModel.getInstance(project).currentSnapshot

        scriptA.dependencies.resolve(storage)
            .flatMap { it.usedInScripts }
            .resolve()

        scriptB.dependencies.resolve(storage)
            .flatMap { it.usedInScripts }
            .resolve()
    }

    fun testThatLibsAreSharedBetweenScripts() {
        val libs = listOf(kotlinTestJs, jetbrainsAnnotations)
        setUpEnvironment(
            aKtsLibs = libs, bKtsLibs = libs
        )

        val script1A = addAndLoadScript("script1.a.kts")
        val script2A = addAndLoadScript("script2.a.kts")
        val scriptB = addAndLoadScript("script.b.kts")

        val (jsLib, annotationsLib) = script1A.dependencies.toList()

        assertThat("No shared libraries found", script2A.dependencies, contains(`is`(jsLib), `is`(annotationsLib)))
        assertThat("No shared libraries found", scriptB.dependencies, contains(`is`(jsLib), `is`(annotationsLib)))
    }


    // ON UPDATE

    fun testThatLibsAdditionDoesntBreakModel() {
        val environment = setUpEnvironment(
            aKtsLibs = listOf(jetbrainsAnnotations),
            bKtsLibs = listOf(kotlinTestJs)
        )

        val scriptABefore = addAndLoadScript("script.a.kts")
        val scriptBBefore = addAndLoadScript("script.b.kts")

        environment.update(
            aKtsLibs = listOf(
                jetbrainsAnnotations,
                kotlinScriptingJvm,
                kotlinScriptingCommon
            ),
            bKtsLibs = listOf(kotlinTestJs, jetbrainsAnnotations)
        )

        refreshDependencies(scriptABefore.path)
        refreshDependencies(scriptBBefore.path)

        val storage = WorkspaceModel.getInstance(project).currentSnapshot

        val scriptEntities = storage.entities(KotlinScriptEntity::class.java).toList()

        scriptEntities.assertContainsOnly(
            "Model contains not expected set of scripts",
            mapOf("path" to scriptABefore.path),
            mapOf("path" to scriptBBefore.path)
        )

        storage.entities(KotlinScriptLibraryEntity::class.java).toList().assertContainsOnly(
            "Model contains not expected set of libraries",
            mapOf("name" to jetbrainsAnnotations.path),
            mapOf("name" to kotlinScriptingJvm.path),
            mapOf("name" to kotlinScriptingCommon.path),
            mapOf("name" to kotlinTestJs.path)
        )

        val scriptAAfter = scriptEntities.single { it.path == scriptABefore.path }
        val scriptBAfter = scriptEntities.single { it.path == scriptBBefore.path }

        scriptAAfter.dependencies.assertContainsOnly(
            "Dependencies update is not detected",
            mapOf("name" to jetbrainsAnnotations.path),
            mapOf("name" to kotlinScriptingJvm.path),
            mapOf("name" to kotlinScriptingCommon.path)
        )

        scriptBAfter.dependencies.assertContainsOnly(
            "Dependencies update is not detected",
            mapOf("name" to kotlinTestJs.path),
            mapOf("name" to jetbrainsAnnotations.path)
        )

        scriptAAfter.dependencies.resolve().assertContainsOnly(
            "Resolved script dependencies are broken",
            mapOf(
                "name" to jetbrainsAnnotations.path,
                "usedInScripts" to listOf(
                    mapOf("path" to scriptAAfter.path),
                    mapOf("path" to scriptBAfter.path)
                )
            ),
            mapOf(
                "name" to kotlinScriptingJvm.path,
                "usedInScripts" to listOf(
                    mapOf("path" to scriptAAfter.path)
                )
            ),
            mapOf(
                "name" to kotlinScriptingCommon.path,
                "usedInScripts" to listOf(
                    mapOf("path" to scriptAAfter.path)
                )
            )
        )

        scriptBAfter.dependencies.resolve().assertContainsOnly(
            "Resolved script dependencies are broken",
            mapOf(
                "name" to kotlinTestJs.path,
                "usedInScripts" to listOf(
                    mapOf("path" to scriptBAfter.path)
                )
            ),
            mapOf(
                "name" to jetbrainsAnnotations.path,
                "usedInScripts" to listOf(
                    mapOf("path" to scriptAAfter.path),
                    mapOf("path" to scriptBAfter.path)
                )
            )
        )
    }

    fun testThatPartialLibsRemovalDoesntBreakModel() {
        val environment = setUpEnvironment(
            aKtsLibs = listOf(
                jetbrainsAnnotations/* to be removed entirely */,
                kotlinScriptingJvm /* remains */,
                kotlinTestJs /* reference to be removed */
            ),
            bKtsLibs = listOf(kotlinTestJs, jetbrainsAnnotations)
        )

        val scriptABefore = addAndLoadScript("script.a.kts")
        val scriptBBefore = addAndLoadScript("script.b.kts")

        environment.update(
            aKtsLibs = listOf(kotlinScriptingJvm),
            bKtsLibs = listOf(kotlinTestJs)
        )

        refreshDependencies(scriptABefore.path)
        refreshDependencies(scriptBBefore.path)

        val storage = WorkspaceModel.getInstance(project).currentSnapshot

        val scriptEntities = storage.entities(KotlinScriptEntity::class.java).toList()
        scriptEntities.assertContainsOnly(
            "Libraries update (removal) breaks script entities",
            mapOf(
                "path" to scriptABefore.path,
                "dependencies" to listOf(
                    mapOf("name" to kotlinScriptingJvm.path),
                )
            ),
            mapOf(
                "path" to scriptBBefore.path,
                "dependencies" to listOf(
                    mapOf("name" to kotlinTestJs.path)
                )
            )
        )

        val libraryEntities = storage.entities(KotlinScriptLibraryEntity::class.java).toList()
        libraryEntities.assertContainsOnly(
            "Libraries update (removal) breaks library entities",
            mapOf(
                "name" to kotlinScriptingJvm.path,
                "usedInScripts" to listOf(
                    mapOf("path" to scriptABefore.path)
                )
            ),
            mapOf(
                "name" to kotlinTestJs.path,
                "usedInScripts" to listOf(
                    mapOf("path" to scriptBBefore.path)
                )
            )
        )
    }

    fun testThatScriptRemovalDoesntBreakModel() {
        setUpEnvironment(
            aKtsLibs = listOf(jetbrainsAnnotations, kotlinScriptingJvm, kotlinTestJs),
            bKtsLibs = listOf(kotlinTestJs)
        )

        addScript("script.a.kts")
        val scriptBBefore = addAndLoadScript("script.b.kts")

        removeScript("script.a.kts")

        refreshDependencies(scriptBBefore.path)

        val storage = WorkspaceModel.getInstance(project).currentSnapshot

        val scriptEntities = storage.entities(KotlinScriptEntity::class.java).toList()
        scriptEntities.assertContainsOnly(
            "Script removal breaks other script entities",
            mapOf(
                "path" to scriptBBefore.path,
                "dependencies" to listOf(
                    mapOf("name" to kotlinTestJs.path)
                )
            )
        )

        val libraryEntities = storage.entities(KotlinScriptLibraryEntity::class.java).toList()
        libraryEntities.assertContainsOnly(
            "Script removal breaks library entities",
            mapOf(
                "name" to kotlinTestJs.path,
                "usedInScripts" to listOf(
                    mapOf("path" to scriptBBefore.path)
                )
            )
        )
    }

    private fun setUpEnvironment(aKtsLibs: List<File>, bKtsLibs: List<File>): TestEnvironment {
        return prepareScriptDefinitions(
            project, getTestName(false), scriptDefinitionSourcePath, testRootDisposable,
            aKtsLibs, bKtsLibs
        )
    }

    private fun loadScript(name: String): KotlinScriptEntity {
        val storage = WorkspaceModel.getInstance(project).currentSnapshot
        val scriptEntities = storage.entities(KotlinScriptEntity::class.java).toList()
        return scriptEntities.find { it.path.endsWith(name) } ?: error("Script with name '$name' doesn't exist")
    }

    private fun <E : WorkspaceEntityWithSymbolicId> Collection<SymbolicEntityId<E>>.resolve(
        storage: EntityStorage = WorkspaceModel.getInstance(project).currentSnapshot
    ): List<E> = map { it.resolve(storage) ?: error("Unresolvable ref: ${it}") }

    private fun addScript(fileName: String): String {
      val projectBasePath = Files.createDirectories(Path.of(project.basePath ?: error("Project basePath doesn't exist")))
      val scriptFile = projectBasePath.resolve(fileName).createParentDirectories().createFile().toFile()

      refreshDependencies(scriptFile.absolutePath)
      return scriptFile.absolutePath
    }

    private fun removeScript(fileName: String) {
        val projectBasePath = Files.createDirectories(Path.of(project.basePath ?: error("Project basePath doesn't exist")))
        val scriptFile = projectBasePath.resolve(fileName).toFile().also { it.delete() }
        VfsUtil.markDirtyAndRefresh(false, false, false, scriptFile)
        ScriptConfigurationManager.getInstance(project).updateScriptDefinitionReferences()
    }

    private fun refreshDependencies(path: String) {
        val scriptFile = File(path)
        val scriptVirtualFile = VfsUtil.findFileByIoFile(scriptFile, true) ?: error("No VFS file for ${scriptFile.absolutePath}")
        val scriptPsiFile =
            PsiManager.getInstance(project).findFile(scriptVirtualFile) ?: error("No PSI file for ${scriptFile.absolutePath}")
        ScriptConfigurationManager.updateScriptDependenciesSynchronously(scriptPsiFile)
    }

    private fun addAndLoadScript(fileName: String): KotlinScriptEntity {
        addScript(fileName)
        return loadScript(fileName)
    }
}
