// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.deft.codegen.patcher

import com.intellij.workspaceModel.codegen.skippedGenTypes
import deft.storage.codegen.generatedApiCode
import deft.storage.codegen.generatedExtensionCode
import org.jetbrains.deft.codegen.model.*
import org.jetbrains.deft.codegen.utils.Imports
import storage.codegen.patcher.KotlinWriter


fun KtFile.rewrite(): String {
  val toImport = Imports(pkg.fqn)
  toImport.set.add("org.jetbrains.deft.ObjBuilder")
  toImport.set.add("com.intellij.workspaceModel.storage.EntitySource")

  val code = mutableMapOf<KtBlock, Pair<String, String>>()
  block.visitRecursively { block, objType ->
    if (objType != null && objType.name !in skippedGenTypes) {
      val body = objType.def.body
      val indent = body.indent
      val parentIndent = body.parent?.indent ?: ""
      code[block] = toImport.findAndRemoveFqns(objType.generatedApiCode(indent)) to toImport.findAndRemoveFqns(objType.generatedExtensionCode(parentIndent))
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
      var oldCode = block._generatedCode
      if (oldCode != null) {
        w.removeBlock(oldCode)
      }
      oldCode = block._extensionCode
      if (oldCode != null) {
        w.removeBlock(oldCode)
      }
    }
    else {
      w.addGeneratedCodeBlock(objType!!, newCode.first)
      w.addExtensionBlock(objType, newCode.second)
    }
  }

  w.end()

  return w.result.toString()
}

private fun KotlinWriter.addExtensionBlock(defType: DefType, code: String) {
  val body = defType.def.body
  val extensionCode = body._extensionCode
  if (code.isEmpty()) {
    if (extensionCode != null) removeBlock(extensionCode)
    return
  }

  if (extensionCode == null) {
    addTo(body.range!!.range.last + 2)
    result.append("\n")
    result.append(code)
  } else {
    addTo(extensionCode.first)
    result.append(code)
    skipTo(extensionCode.last)
  }
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
  }
  else if (generatedCode == null) {
    addTo(body.range!!.range.last)
    result.append("\n")
    result.append(newCode)
    result.append("\n$parentIndent")
  }
  else {
    addTo(generatedCode.first)
    result.append(newCode)
    skipTo(generatedCode.last)
  }
}