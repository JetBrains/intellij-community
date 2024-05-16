// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.sealedInheritors

import com.google.gson.JsonObject
import com.intellij.openapi.application.readAction
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.psi.classIdIfNonLocal
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.base.util.getAsJsonObjectList
import org.jetbrains.kotlin.idea.base.util.getString
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.projectStructureTest.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClass
import java.io.File
import kotlin.io.path.Path

abstract class AbstractSealedInheritorsProviderTest : AbstractProjectStructureTest<SealedInheritorsProviderTestProjectStructure>(
    SealedInheritorsProviderTestProjectStructureParser,
) {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun getTestDataDirectory(): File =
        KotlinRoot.DIR.resolve("base").resolve("fir").resolve("analysis-api-providers").resolve("testData").resolve("sealedInheritors")

    override fun doTestWithProjectStructure(testDirectory: String) {
        testProjectStructure.targets.forEach { checkTargetFile(it, testDirectory) }
    }

    private fun checkTargetFile(testTarget: SealedInheritorsProviderTestTarget, testDirectory: String) {
        val module = modulesByName[testTarget.moduleName] ?: error("The target module `${testTarget.moduleName}` does not exist.")
        val ktFile = module.findSourceKtFile(testTarget.filePath)

        val targetClass = ktFile.findReferenceAt(getCaretPosition(ktFile))?.resolve() as? KtClass
            ?: error("Expected a `${KtClass::class.simpleName}` reference at the caret position.")
        val actualInheritors = resolveActualInheritors(targetClass)

        val expectedFile = Path(testDirectory, testTarget.moduleName, testTarget.filePath.removeSuffix(".kt") + ".txt")
        val renderedInheritors = actualInheritors.joinToString("\n")
        KotlinTestUtils.assertEqualsToFile(expectedFile, renderedInheritors)
    }

    private fun resolveActualInheritors(targetClass: KtClass): List<ClassId> = runBlocking {
        readAction {
            assertTrue("Expected the target type `${targetClass.classIdIfNonLocal}` to be sealed.", targetClass.isSealed())

            analyze(targetClass) {
                val classSymbol = targetClass.getNamedClassOrObjectSymbol()
                    ?: error("Expected the target class `${targetClass.classIdIfNonLocal}` to have a class or object symbol.")

                classSymbol.getSealedClassInheritors().map { it.classIdIfNonLocal ?: error("Sealed class inheritors should not be local.") }
            }
        }
    }
}

data class SealedInheritorsProviderTestProjectStructure(
    override val libraries: List<TestProjectLibrary>,
    override val modules: List<TestProjectModule>,
    val targets: List<SealedInheritorsProviderTestTarget>,
) : TestProjectStructure

/**
 * The test target file identified by [moduleName] and [filePath] must contain a top-level property `target`. The test checks the sealed
 * inheritors of that property's type.
 */
data class SealedInheritorsProviderTestTarget(
    val moduleName: String,
    val filePath: String,
)

object SealedInheritorsProviderTestProjectStructureParser : TestProjectStructureParser<SealedInheritorsProviderTestProjectStructure> {
    override fun parse(
        libraries: List<TestProjectLibrary>,
        modules: List<TestProjectModule>,
        json: JsonObject,
    ): SealedInheritorsProviderTestProjectStructure {
        val targetObjects = json.getAsJsonObjectList("targets") ?: error("Expected at least one target.")
        val targets = targetObjects.map { jsonObject ->
            SealedInheritorsProviderTestTarget(
                moduleName = jsonObject.getString("module"),
                filePath = jsonObject.getString("file"),
            )
        }

        return SealedInheritorsProviderTestProjectStructure(libraries, modules, targets)
    }
}
