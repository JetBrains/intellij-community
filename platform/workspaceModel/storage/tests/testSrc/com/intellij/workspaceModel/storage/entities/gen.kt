package com.intellij.workspace.model

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
    val deftRoot = File("").absoluteFile
    val dir = deftRoot.resolve("community/platform/workspaceModel/storage/tests/testSrc/com/intellij/workspaceModel/storage/entities")
    CodeWriter().generate(dir, "api", "impl", "org.jetbrains.deft.IntellijWs")
}

fun DefType.implIjWsFileContents(simpleTypes: List<DefType>): String {
    return fileContents(def.file!!.pkg.fqn, """
            ${implWsCode(simpleTypes)}
        """.trim())
}

open class CodeWriter() {
    open fun File.writeCode(code: String) {
        writeCodeInternal(this, code)
    }

    private fun writeCodeInternal(file: File, code: String) {
        file.writeText(code)
    }

    fun generate(dir: File, fromDirectory: String, toDirectory: String, moduleId: String) {
        val generatedDestDir = dir.resolve(toDirectory)
        val ktSrcs = dir.resolve(fromDirectory).listFiles()!!
            .toList()
            .filter { it.name.endsWith(".kt") }

        val module = KtObjModule(ObjModule.Id(moduleId))
        ktSrcs.forEach {
            module.addFile(it.relativeTo(dir).path) { it.readText() }
        }
        val result = module.build()
        module.files.forEach {
            dir.resolve(it.name).writeCode(it.rewrite())
        }
        result.typeDefs.filterNot { it.name == "WorkspaceEntity" || it.name == "WorkspaceEntityWithPersistentId" || it.abstract }.forEach {
            generatedDestDir
                .resolve(it.javaImplName + ".kt")
                .writeCode(it.implIjWsFileContents(result.simpleTypes))
        }
        dir.resolve(module.moduleObjName + ".kt").writeCode(result.wsModuleCode())
//        dir.resolve("toIjWs/generated.kt").writeCode(result.ijWsCode())
    }
}