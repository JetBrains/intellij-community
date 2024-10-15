// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.inheritors

import com.google.gson.JsonObject
import com.intellij.openapi.application.readAction
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.base.util.getAsJsonObjectList
import org.jetbrains.kotlin.idea.base.util.getString
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.projectStructureTest.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClass
import kotlin.io.path.Path

abstract class AbstractInheritorsProviderTest : AbstractProjectStructureTest<InheritorsProviderTestProjectStructure>(
    InheritorsProviderTestProjectStructureParser,
) {
    /**
     * Resolves the inheritors of [targetClass]. This function will be called in a read action.
     */
    protected abstract fun resolveInheritors(targetClass: KtClass, useSiteModule: KaModule): List<ClassId>

    override fun doTestWithProjectStructure(testDirectory: String) {
        testProjectStructure.targets.forEach { checkTargetFile(it, testDirectory) }
    }

    private fun checkTargetFile(testTarget: InheritorsProviderTestTarget, testDirectory: String) {
        val module = modulesByName[testTarget.moduleName] ?: error("The target module `${testTarget.moduleName}` does not exist.")
        val ktFile = module.findSourceKtFile(testTarget.filePath)
        val kaModule = ktFile.getKaModule(project, useSiteModule = null)

        val targetClass = ktFile.findReferenceAt(getCaretPosition(ktFile))?.resolve() as? KtClass
            ?: error("Expected a `${KtClass::class.simpleName}` reference at the caret position.")

        val actualInheritors = runBlocking {
            readAction {
                resolveInheritors(targetClass, kaModule).sortedBy { it.toString() }
            }
        }

        val expectedFile = Path(testDirectory, testTarget.moduleName, testTarget.filePath.removeSuffix(".kt") + ".txt")
        val renderedInheritors = actualInheritors.joinToString("\n")
        KotlinTestUtils.assertEqualsToFile(expectedFile, renderedInheritors)
    }
}

data class InheritorsProviderTestProjectStructure(
    override val libraries: List<TestProjectLibrary>,
    override val modules: List<TestProjectModule>,
    val targets: List<InheritorsProviderTestTarget>,
) : TestProjectStructure

/**
 * The test target file identified by [moduleName] and [filePath] must contain a top-level property `target`. The test checks the inheritors
 * of that property's type.
 */
data class InheritorsProviderTestTarget(
    val moduleName: String,
    val filePath: String,
)

object InheritorsProviderTestProjectStructureParser : TestProjectStructureParser<InheritorsProviderTestProjectStructure> {
    override fun parse(
        libraries: List<TestProjectLibrary>,
        modules: List<TestProjectModule>,
        json: JsonObject,
    ): InheritorsProviderTestProjectStructure {
        val targetObjects = json.getAsJsonObjectList("targets") ?: error("Expected at least one target.")
        val targets = targetObjects.map { jsonObject ->
            InheritorsProviderTestTarget(
                moduleName = jsonObject.getString("module"),
                filePath = jsonObject.getString("file"),
            )
        }

        return InheritorsProviderTestProjectStructure(libraries, modules, targets)
    }
}
