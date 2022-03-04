package org.jetbrains.deft.codegen.patcher

import deft.storage.codegen.generatedApiCode
import org.jetbrains.deft.codegen.utils.Imports
import storage.codegen.patcher.DefType
import storage.codegen.patcher.KotlinWriter
import storage.codegen.patcher.KtBlock
import storage.codegen.patcher.KtFile

fun KtFile.rewrite(cleanUpOnly: Boolean = false): String {
    val toImport = Imports(pkg.fqn)
    toImport.set.add("org.jetbrains.deft.Obj")
    toImport.set.add("org.jetbrains.deft.ObjBuilder")
    toImport.set.add("org.jetbrains.deft.impl.*")
    toImport.set.add("org.jetbrains.deft.impl.fields.*")
    toImport.set.add("com.intellij.workspaceModel.storage.EntitySource")

    val code = mutableMapOf<KtBlock, String>()
    if (!cleanUpOnly) {
        block.visitRecursively { block, objType ->
            if (objType != null) {
                val body = objType.def.body
                val indent = body.indent
                code[block] = toImport.findAndRemoveFqns(objType.generatedApiCode(indent))
            }
        }
    }

    val w = KotlinWriter(content())
    
    if (code.isNotEmpty()) {
        w.addImports(imports, toImport.set.sorted())
    }

    block.visitRecursively { block, objType ->
        val newCode = code[block]
        if (newCode == null) {
            // remove old generated code
            val oldCode = block._generatedCode
            if (oldCode != null) {
                w.removeBlock(oldCode)
            }
        } else {
            w.addGeneratedCodeBlock(objType!!, newCode)
        }
    }

    w.end()

    return w.result.toString()
}

private fun KotlinWriter.addGeneratedCodeBlock(it: DefType, newCode: String) {
    val body = it.def.body
    val indent = body.indent
    val parentIndent = body.parent?.indent ?: ""
    val generatedCode = body._generatedCode
    if (body.isStub) {
        addTo(body.prevElementEnd.pos)
        result.append(" {")
        result.append("\n")
        result.append(newCode)
        result.append("\n$parentIndent")
        result.append("}")
    } else if (generatedCode == null) {
        addTo(body.range!!.range.last)
        result.append("\n")
        result.append(newCode)
        result.append("\n$parentIndent")
    } else {
        addTo(generatedCode.first)
        result.append(newCode)
        skipTo(generatedCode.last)
    }
}