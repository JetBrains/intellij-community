package com.intellij.workspace.model

import com.intellij.workspaceModel.codegen.CodeWriter
import java.io.File

fun main() {
    val productionModuleRoot = File("community/platform/workspaceModel/storage/src/com/intellij/workspaceModel/storage/bridgeEntities").absoluteFile
    val productionModuleRootOutput = File("community/platform/workspaceModel/storage/gen/com/intellij/workspaceModel/storage/bridgeEntities/api").absoluteFile
    CodeWriter.generate(productionModuleRoot, "api", productionModuleRootOutput, false)

    val productionModuleRootTestEntity = File("community/platform/workspaceModel/storage/testEntities/testSrc/com/intellij/workspaceModel/storage/entities/model").absoluteFile
    val productionModuleRootOutputTestEntity = File("community/platform/workspaceModel/storage/testEntities/testSrc/com/intellij/workspaceModel/storage/entities/model/api").absoluteFile
    CodeWriter.generate(productionModuleRootTestEntity, "", productionModuleRootOutputTestEntity, false)

    val testRoots = File("community/platform/workspaceModel/storage/testEntities/testSrc/com/intellij/workspaceModel/storage/entities").absoluteFile
    val testRootsOutput = File("community/platform/workspaceModel/storage/testEntities/gen/com/intellij/workspaceModel/storage/entities/test/api").absoluteFile
    //CodeWriter.generate(testRoots.resolve("model"), "api", "impl")
    CodeWriter.generate(testRoots.resolve("test"), "api", testRootsOutput, false)

    val testUnknownRoots = File("community/platform/workspaceModel/storage/testEntities/testSrc/com/intellij/workspaceModel/storage/entities/unknowntypes").absoluteFile
    val testUnknownRootsOutput = File("community/platform/workspaceModel/storage/testEntities/gen/com/intellij/workspaceModel/storage/entities/unknowntypes/test/api").absoluteFile
    CodeWriter.generate(testUnknownRoots.resolve("test"), "api", testUnknownRootsOutput, true)
}