// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.registerExtension
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.io.Decompressor
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.core.script.configurations.KotlinScriptService
import org.jetbrains.kotlin.idea.core.script.definitions.ScriptDefinitionsModificationTracker
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import java.io.File
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.createScriptDefinitionFromTemplate
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.templates.standard.ScriptTemplateWithArgs

/** Checks that a script resolves a class from a class directory on the classpath of its definition. */
class KotlinScriptClassDirectoryDependencyTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

    override fun runInDispatchThread(): Boolean = false

    // KTIJ-40452: Gradle sync makes `build/classes/kotlin/main` the compiler output of the module
    fun testClassDirectoryIsCompilerOutputOfModule() {
        val classes = compileHelperTo(FileUtil.createTempDirectory("output", null, true))
        runInEdtAndWait { PsiTestUtil.setCompilerOutputPath(module, refreshAndFind(classes).url, false) }
        try {
            checkHelperResolves(classes)
        }
        finally {
            runInEdtAndWait {
                ModuleRootModificationUtil.updateModel(module) {
                    it.getModuleExtension(CompilerModuleExtension::class.java).inheritCompilerOutputPath(true)
                }
            }
        }
    }

    fun testClassDirectoryOutsideProject() {
        checkHelperResolves(compileHelperTo(FileUtil.createTempDirectory("classes", null, true)))
    }

    private fun checkHelperResolves(classes: File) {
        registerDefinition(classes)
        val script = myFixture.tempDirFixture.createFile("scripts/script.probe.kts", "dep.Helper()")

        runBlocking {
            KotlinScriptService.getInstance(project).load(script)
        }

        runInEdtAndWait {
            myFixture.configureFromExistingVirtualFile(script)
            assertEmpty(myFixture.doHighlighting(HighlightSeverity.ERROR).map { it.description })
        }
    }

    private fun compileHelperTo(target: File): File {
        val sources = FileUtil.createTempDirectory("sources", null, true)
        File(sources, "Helper.kt").writeText("package dep\n\nclass Helper")
        Decompressor.Zip(KotlinCompilerStandalone(listOf(sources)).compile().toPath()).extract(target.toPath())
        refreshAndFind(target)
        return target
    }

    private fun refreshAndFind(file: File): VirtualFile =
        requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)) { "No virtual file for $file" }

    private fun registerDefinition(classes: File) {
        val definition = createScriptDefinitionFromTemplate(
            KotlinType(ScriptTemplateWithArgs::class),
            defaultJvmScriptingHostConfiguration,
            compilation = {
                fileExtension("probe.kts")
                ide { acceptedLocations(ScriptAcceptedLocation.Everywhere) }
                updateClasspath(listOf(classes, TestKotlinArtifacts.kotlinStdlib.toFile(), TestKotlinArtifacts.kotlinScriptRuntime.toFile()))
            },
        )
        project.registerExtension(
            ScriptDefinitionsProvider.EP_NAME,
            object : ScriptDefinitionsProvider {
                override val id: String = "KotlinScriptClassDirectoryDependencyTest"
                override fun provideDefinitions(
                    baseHostConfiguration: ScriptingHostConfiguration,
                    loadedScriptDefinitions: List<ScriptDefinition>,
                ): Iterable<ScriptDefinition> = listOf(definition)
            },
            testRootDisposable,
        )
        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
    }
}
