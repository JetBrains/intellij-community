// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.cri.CriToolchain
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.name.FqName
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.div

@OptIn(ExperimentalBuildToolsApi::class)
internal object BtaTestFixtureSupport {
    val sampleFixtureDir: Path = KotlinRoot.PATH.resolve("compiler-reference-index/tests.k2/testData/bta/sample")
    val animalFqName = FqName("fixtures.Animal")
    val animalLookupPaths = setOf(
        "src/main/kotlin/fixtures/Animal.kt",
        "src/main/kotlin/fixtures/Dog.kt",
        "src/main/kotlin/fixtures/Cat.kt",
        "src/main/kotlin/fixtures/Main.kt",
    )
    val unrelatedLookupPath = "src/main/kotlin/fixtures/Car.kt"
    val animalDirectSubtypes = setOf(FqName("fixtures.Dog"), FqName("fixtures.Cat"))
    val animalDeepSubtypes = animalDirectSubtypes + FqName("fixtures.Poodle")

    fun copyFixtureInto(criRoot: Path) {
        criRoot.createDirectories()
        for (name in listOf(
            CriToolchain.LOOKUPS_FILENAME,
            CriToolchain.FILE_IDS_TO_PATHS_FILENAME,
            CriToolchain.SUBTYPES_FILENAME,
        )) {
            (sampleFixtureDir / name).copyTo(criRoot / name, overwrite = true)
        }
    }
}
