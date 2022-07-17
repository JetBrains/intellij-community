// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.patcher

import com.intellij.workspaceModel.codegen.generatedApiCode
import com.intellij.workspaceModel.codegen.generatedExtensionCode
import com.intellij.workspaceModel.codegen.deft.model.*
import com.intellij.workspaceModel.codegen.utils.Imports
import com.intellij.workspaceModel.codegen.patcher.KotlinWriter


fun KtFile.rewrite(): String? {
  val toImport = Imports(pkg.fqn)
  toImport.set.add("org.jetbrains.deft.ObjBuilder")
  toImport.set.add("com.intellij.workspaceModel.storage.EntitySource")

  val code = mutableMapOf<KtBlock, Pair<String, String>>()
  block.visitRecursively { block, objType ->
    if (objType != null && !objType.utilityType) {
      val body = objType.def.body
      val indent = body.indent
      val parentIndent = body.parent?.indent ?: ""
      code[block] = toImport.findAndRemoveFqns(objType.generatedApiCode(indent, body.generatedCode == null)) to toImport.findAndRemoveFqns(objType.generatedExtensionCode(parentIndent))
    }
  }
  if (code.isEmpty()) return null

  val w = KotlinWriter(content())

  if (code.isNotEmpty()) {
    w.addImports(imports, toImport.set.sorted())
  }

  var hasSomethingToWrite = false
  block.visitRecursively { block, objType ->
    val newCode = code[block]
    if (newCode == null) {
      // remove old generated code
      var oldCode = block._generatedCode
      if (oldCode != null) {
        w.removeBlock(oldCode)
        hasSomethingToWrite = true
      }
      oldCode = block._extensionCode
      if (oldCode != null) {
        w.removeBlock(oldCode)
        hasSomethingToWrite = true
      }
    }
    else {
      hasSomethingToWrite = true
      w.addGeneratedCodeBlock(objType!!, newCode.first)
      w.addExtensionBlock(objType, newCode.second)
    }
  }

  w.end()

  return if (hasSomethingToWrite) w.result.toString() else null
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
  if (generatedCode == null) {
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