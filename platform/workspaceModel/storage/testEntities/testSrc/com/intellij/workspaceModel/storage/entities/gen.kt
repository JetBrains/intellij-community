package com.intellij.workspace.model

import com.intellij.workspaceModel.codegen.CodeWriter
import deft.storage.codegen.javaImplName
import org.jetbrains.deft.codegen.ijws.implWsCode
import org.jetbrains.deft.codegen.ijws.wsModuleCode
import org.jetbrains.deft.codegen.model.DefType
import org.jetbrains.deft.codegen.model.KtObjModule
import org.jetbrains.deft.codegen.patcher.rewrite
import org.jetbrains.deft.codegen.utils.fileContents
import org.jetbrains.deft.impl.ObjModule
import java.io.File

fun main() {
    val productionModuleRoot = File("community/platform/workspaceModel/storage/src/com/intellij/workspaceModel/storage/bridgeEntities").absoluteFile
    CodeWriter.generate(productionModuleRoot, "api", "impl", "org.jetbrains.workspaceModel")
    val testRoots = File("community/platform/workspaceModel/storage/testEntities/testSrc/com/intellij/workspaceModel/storage/entities").absoluteFile
    CodeWriter.generate(testRoots.resolve("model"), "api", "impl", "org.jetbrains.deft.IntellijWs")
    CodeWriter.generate(testRoots.resolve("test"), "api", "impl", "org.jetbrains.deft.TestEntities")
}