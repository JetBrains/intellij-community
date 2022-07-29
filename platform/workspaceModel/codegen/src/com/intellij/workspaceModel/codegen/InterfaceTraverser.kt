// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen

import com.intellij.workspaceModel.codegen.deft.model.DefType
import com.intellij.workspaceModel.codegen.deft.model.KtInterfaceKind
import com.intellij.workspaceModel.codegen.deft.model.WsData
import com.intellij.workspaceModel.codegen.deft.*
import com.intellij.workspaceModel.codegen.deft.Field

class InterfaceTraverser(
  val simpleTypes: List<DefType>
) {
  fun traverse(myInterface: DefType, visitor: InterfaceVisitor): Boolean {
    for (field in myInterface.structure.declaredFields) {
      val res = traverseField(field, visitor, field.name, myInterface.def.kind)
      if (!res) return false
    }
    return true
  }

  private fun traverseField(field: Field<*, *>, visitor: InterfaceVisitor, varName: String, kind: KtInterfaceKind?): Boolean {
    return traverseType(field.type, visitor, varName, kind)
  }

  private fun traverseType(type: ValueType<*>, visitor: InterfaceVisitor, varName: String, kind: KtInterfaceKind?): Boolean {
    when (type) {
      is TBoolean -> return visitor.visitBoolean(varName)
      is TInt -> return visitor.visitInt(varName)
      is TString -> return visitor.visitString(varName)
      is TCollection<*, *> -> {
        val itemVarName = "_$varName"
        val shouldProcessList = visitor.visitListStart(varName, itemVarName, type.elementType)
        if (!shouldProcessList) return false
        val traversingResult = traverseType(type.elementType, visitor, itemVarName, kind)
        return visitor.visitListEnd(varName, itemVarName, traversingResult, type.elementType)
      }

      is TMap<*, *> -> {
        val keyVarName = "key_$varName"
        val valueVarName = "value_$varName"
        val shouldProcessMap = visitor.visitMapStart(varName, keyVarName, valueVarName, type.keyType, type.valueType)
        if (!shouldProcessMap) return false

        val keyTraverseResult = traverseType(type.keyType, visitor, keyVarName, kind)
        val valueTraverseResult = traverseType(type.valueType, visitor, valueVarName, kind)

        return visitor.visitMapEnd(varName, keyVarName, valueVarName, type.keyType, type.valueType,
                                   keyTraverseResult && valueTraverseResult)
      }
      is TOptional<*> -> {
        val notNullVarName = "_$varName"
        var continueProcess = visitor.visitOptionalStart(varName, notNullVarName, type.type)
        if (!continueProcess) return false

        continueProcess = traverseType(type.type, visitor, notNullVarName, kind)
        return visitor.visitOptionalEnd(varName, notNullVarName, type.type, continueProcess)
      }
      is TBlob<*> -> {
        val foundType = simpleTypes.find { it.name == type.javaSimpleName }
        if (foundType == null) {
          return visitor.visitUnknownBlob(varName, type.javaSimpleName)
        } else {

          if (kind == WsData) {
            var process = visitor.visitDataClassStart(varName, type.javaSimpleName, foundType)
            if (!process) return false

            process = traverse(foundType, visitor)
            return visitor.visitDataClassEnd(varName, type.javaSimpleName, process, foundType)
          }
          return false
          /*


          var process = visitor.visitKnownBlobStart(varName, type.javaSimpleName)
          if (!process) return false

          process = traverse(foundType, visitor)
          return visitor.visitKnownBlobFinish(varName, type.javaSimpleName, process)
          */
        }
      }
      else -> {}
    }
    return true
  }
}

interface InterfaceVisitor {
  fun visitBoolean(varName: String): Boolean
  fun visitInt(varName: String): Boolean
  fun visitString(varName: String): Boolean

  fun visitListStart(varName: String, itemVarName: String, listArgumentType: ValueType<*>): Boolean
  fun visitListEnd(varName: String, itemVarName: String, traverseResult: Boolean, listArgumentType: ValueType<*>): Boolean

  fun visitMapStart(varName: String, keyVarName: String, valueVarName: String, keyType: ValueType<*>, valueType: ValueType<*>): Boolean
  fun visitMapEnd(varName: String,
                  keyVarName: String,
                  valueVarName: String,
                  keyType: ValueType<*>,
                  valueType: ValueType<*>,
                  traverseResult: Boolean): Boolean

  fun visitOptionalStart(varName: String, notNullVarName: String, type: ValueType<*>): Boolean
  fun visitOptionalEnd(varName: String, notNullVarName: String, type: ValueType<*>, traverseResult: Boolean): Boolean

  fun visitUnknownBlob(varName: String, javaSimpleName: String): Boolean
  fun visitKnownBlobStart(varName: String, javaSimpleName: String): Boolean
  fun visitKnownBlobFinish(varName: String, javaSimpleName: String, traverseResult: Boolean): Boolean

  fun visitDataClassStart(varName: String, javaSimpleName: String, foundType: DefType): Boolean
  fun visitDataClassEnd(varName: String, javaSimpleName: String, traverseResult: Boolean, foundType: DefType): Boolean
}

