// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.low.level.api

import com.google.gson.JsonObject
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.resolveToFirSymbol
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.getSealedClassInheritors
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.declarations.utils.isSealed
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.base.util.getAsJsonObjectList
import org.jetbrains.kotlin.idea.base.util.getString
import org.jetbrains.kotlin.idea.fir.resolveWithClearCaches
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.projectStructureTest.AbstractProjectStructureTest
import org.jetbrains.kotlin.idea.test.projectStructureTest.ModulesByName
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestProjectLibrary
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestProjectModule
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestProjectStructure
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestProjectStructureParser
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClass
import java.io.File
import kotlin.io.path.Path

abstract class AbstractFirSealedClassInheritorsTest : AbstractProjectStructureTest<FirSealedClassInheritorsTestProjectStructure>() {
    override fun isFirPlugin(): Boolean = true

    override fun getTestDataDirectory(): File =
        KotlinRoot.DIR.resolve("fir-low-level-api-ide-impl").resolve("testData").resolve("sealedClassInheritors")

    protected fun doTest(testDirectory: String) {
        val (testStructure, projectLibrariesByName, modulesByName) =
            initializeProjectStructure(testDirectory, FirSealedClassInheritorsTestProjectStructureParser)

        testStructure.targets.forEach { checkTargetFile(it, modulesByName, testDirectory) }
    }

    private fun checkTargetFile(testTarget: FirSealedClassInheritorsTestTarget, modulesByName: ModulesByName, testDirectory: String) {
        val module = modulesByName[testTarget.moduleName] ?: error("The target module `${testTarget.moduleName}` does not exist.")
        val ktFile = module.findSourceKtFile(testTarget.filePath)

        val targetClass = ktFile.findReferenceAt(getCaretPosition(ktFile))?.resolve() as? KtClass
            ?: error("Expected a `${KtClass::class.simpleName}` reference at the caret position.")
        val actualInheritors = resolveActualInheritors(targetClass)

        val expectedFile = Path(testDirectory, testTarget.moduleName, testTarget.filePath.removeSuffix(".kt") + ".txt")
        val renderedInheritors = actualInheritors.joinToString("\n")
        KotlinTestUtils.assertEqualsToFile(expectedFile, renderedInheritors)
    }

    @OptIn(SymbolInternals::class)
    private fun resolveActualInheritors(targetClass: KtClass): List<ClassId> =
        resolveWithClearCaches(targetClass) { resolveSession ->
            val targetFirClass = targetClass.resolveToFirSymbol(resolveSession, phase = FirResolvePhase.RAW_FIR).fir as FirRegularClass
            assertTrue("Expected the target type `${targetFirClass.classId}` to be sealed.", targetFirClass.isSealed)
            targetFirClass.getSealedClassInheritors(resolveSession.useSiteFirSession)
        }
}

data class FirSealedClassInheritorsTestProjectStructure(
    override val libraries: List<TestProjectLibrary>,
    override val modules: List<TestProjectModule>,
    val targets: List<FirSealedClassInheritorsTestTarget>,
) : TestProjectStructure

/**
 * The test target file identified by [moduleName] and [filePath] must contain a top-level property `target`. The test checks the sealed
 * inheritors of that property's type.
 */
data class FirSealedClassInheritorsTestTarget(
    val moduleName: String,
    val filePath: String,
)

object FirSealedClassInheritorsTestProjectStructureParser : TestProjectStructureParser<FirSealedClassInheritorsTestProjectStructure> {
    override fun parse(
        libraries: List<TestProjectLibrary>,
        modules: List<TestProjectModule>,
        json: JsonObject,
    ): FirSealedClassInheritorsTestProjectStructure {
        val targetObjects = json.getAsJsonObjectList("targets") ?: error("Expected at least one target.")
        val targets = targetObjects.map { jsonObject ->
            FirSealedClassInheritorsTestTarget(
                moduleName = jsonObject.getString("module"),
                filePath = jsonObject.getString("file"),
            )
        }

        return FirSealedClassInheritorsTestProjectStructure(libraries, modules, targets)
    }
}
