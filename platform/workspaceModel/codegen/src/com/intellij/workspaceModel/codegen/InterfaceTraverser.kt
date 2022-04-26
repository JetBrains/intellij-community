// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen

import org.jetbrains.deft.codegen.model.DefType
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

class InterfaceTraverser(
  val simpleTypes: List<DefType>
) {
  fun traverse(myInterface: ObjType<*, *>, visitor: InterfaceVisitor) {
    for (field in myInterface.structure.declaredFields) {
      val res = traverseField(field, visitor, field.name)
      if (!res) return
    }
  }

  private fun traverseField(field: Field<*, *>, visitor: InterfaceVisitor, varName: String): Boolean {
    return traverseType(field.type, visitor, varName)
  }

  private fun traverseType(type: ValueType<*>, visitor: InterfaceVisitor, varName: String): Boolean {
    when (type) {
      is TBoolean -> return visitor.visitBoolean(varName)
      is TInt -> return visitor.visitInt(varName)
      is TString -> return visitor.visitString(varName)
      is TList<*> -> {
        val itemVarName = "_$varName"
        val shouldProcessList = visitor.visitListStart(varName, itemVarName, type.elementType)
        if (!shouldProcessList) return false
        val traversingResult = traverseType(type.elementType, visitor, itemVarName)
        return visitor.visitListEnd(varName, itemVarName, traversingResult, type.elementType)
      }

      is TMap<*, *> -> {
        val keyVarName = "key_$varName"
        val valueVarName = "value_$varName"
        val shouldProcessMap = visitor.visitMapStart(varName, keyVarName, valueVarName, type.keyType, type.valueType)
        if (!shouldProcessMap) return false

        val keyTraverseResult = traverseType(type.keyType, visitor, keyVarName)
        val valueTraverseResult = traverseType(type.valueType, visitor, valueVarName)

        return visitor.visitMapEnd(varName, keyVarName, valueVarName, type.keyType, type.valueType,
                                   keyTraverseResult && valueTraverseResult)
      }
      is TOptional<*> -> {
        val notNullVarName = "_$varName"
        var continueProcess = visitor.visitOptionalStart(varName, notNullVarName, type.type)
        if (!continueProcess) return false

        continueProcess = traverseType(type.type, visitor, notNullVarName)
        return visitor.visitOptionalEnd(varName, notNullVarName, type.type, continueProcess)
      }
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
}

