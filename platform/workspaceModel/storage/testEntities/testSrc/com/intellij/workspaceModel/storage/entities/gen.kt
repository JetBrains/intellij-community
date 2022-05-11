package com.intellij.workspace.model

import com.intellij.workspaceModel.codegen.CodeWriter
import java.io.File

fun main() {
    val productionModuleRoot = File("community/platform/workspaceModel/storage/src/com/intellij/workspaceModel/storage/bridgeEntities").absoluteFile
    val productionModuleRootOutput = File("community/platform/workspaceModel/storage/gen/com/intellij/workspaceModel/storage/bridgeEntities/api").absoluteFile
    CodeWriter.generate(productionModuleRoot, "api", productionModuleRootOutput)
    val testRoots = File("community/platform/workspaceModel/storage/testEntities/testSrc/com/intellij/workspaceModel/storage/entities").absoluteFile
    val testRootsOutput = File("community/platform/workspaceModel/storage/testEntities/gen/com/intellij/workspaceModel/storage/entities/test/api").absoluteFile
    //CodeWriter.generate(testRoots.resolve("model"), "api", "impl")
    CodeWriter.generate(testRoots.resolve("test"), "api", testRootsOutput)
}