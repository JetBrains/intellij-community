// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.OwnProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.fields.javaType
import com.intellij.workspaceModel.codegen.utils.LinesBuilder

class InterfaceTraverser {
  fun traverse(myInterface: ObjClass<*>, visitor: InterfaceVisitor): Boolean {
    for (field in myInterface.fields) {
      val res = traverseField(field, visitor, field.name)
      if (!res) return false
    }
    return true
  }

  private fun traverseField(field: OwnProperty<*, *>, visitor: InterfaceVisitor, varName: String): Boolean {
    return traverseType(field.valueType, visitor, varName)
  }

  private fun traverseType(type: ValueType<*>, visitor: InterfaceVisitor, varName: String): Boolean {
    when (type) {
      is ValueType.Boolean -> return visitor.visitBoolean(varName)
      is ValueType.Int -> return visitor.visitInt(varName)
      is ValueType.String -> return visitor.visitString(varName)
      is ValueType.Collection<*, *> -> {
        val itemVarName = "_$varName"
        val shouldProcessList = visitor.visitListStart(varName, itemVarName, type.elementType)
        if (!shouldProcessList) return false
        val traversingResult = traverseType(type.elementType, visitor, itemVarName)
        return visitor.visitListEnd(varName, itemVarName, traversingResult, type.elementType)
      }

      is ValueType.Map<*, *> -> {
        val keyVarName = "key_$varName"
        val valueVarName = "value_$varName"
        val shouldProcessMap = visitor.visitMapStart(varName, keyVarName, valueVarName, type.keyType, type.valueType)
        if (!shouldProcessMap) return false

        val keyTraverseResult = traverseType(type.keyType, visitor, keyVarName)
        val valueTraverseResult = traverseType(type.valueType, visitor, valueVarName)

        return visitor.visitMapEnd(varName, keyVarName, valueVarName, type.keyType, type.valueType,
                                   keyTraverseResult && valueTraverseResult)
      }
      is ValueType.Optional<*> -> {
        val notNullVarName = "_$varName"
        var continueProcess = visitor.visitOptionalStart(varName, notNullVarName, type.type)
        if (!continueProcess) return false

        continueProcess = traverseType(type.type, visitor, notNullVarName)
        return visitor.visitOptionalEnd(varName, notNullVarName, type.type, continueProcess)
      }
      is ValueType.Blob<*> -> {
        return visitor.visitUnknownBlob(varName, type.javaClassName)
          /*


          var process = visitor.visitKnownBlobStart(varName, type.javaSimpleName)
          if (!process) return false

          process = traverse(foundType, visitor)
          return visitor.visitKnownBlobFinish(varName, type.javaSimpleName, process)
          */
      }
      is ValueType.DataClass<*> -> {
        var process = visitor.visitDataClassStart(varName, type)
        if (!process) return false

        //process = traverse(type, visitor)
        return visitor.visitDataClassEnd(varName, type, process)
      }
      else -> {}
    }
    return true
  }
}

class DeserializationVisitor(linesBuilder: LinesBuilder) : InterfaceVisitor {
  private val builders: ArrayDeque<LinesBuilder> = ArrayDeque<LinesBuilder>().also { it.add(linesBuilder) }
  private val builder: LinesBuilder
    get() = builders.last()

  private var countersCounter = 0

  override fun visitBoolean(varName: String): Boolean {
    builder.line("$varName = de.readBoolean()")
    return true
  }

  override fun visitInt(varName: String): Boolean {
    builder.line("$varName = de.readInt()")
    return true
  }

  override fun visitString(varName: String): Boolean {
    builder.line("$varName = de.readString()")
    return true
  }

  override fun visitListStart(varName: String, itemVarName: String, listArgumentType: ValueType<*>): Boolean {
    if (listArgumentType.isRefType()) return true

    val sub = LinesBuilder(StringBuilder(), builder.indentLevel + 1)
    builders.add(sub)
    return true
  }

  override fun visitListEnd(varName: String, itemVarName: String, traverseResult: Boolean, listArgumentType: ValueType<*>): Boolean {
    if (listArgumentType.isRefType()) return true

    val myInListBuilder = builders.removeLast()
    if (myInListBuilder.result.isNotBlank()) {
      val varCounter = "counter" + counter()
      val varCollector = "collector" + counter()
      builder.line("val $varCounter = de.readInt()")
      builder.line("val $varCollector = ArrayList<${listArgumentType.javaType}>()")
      builder.line("var $itemVarName: ${listArgumentType.javaType}")
      builder.line("repeat($varCounter) {")
      myInListBuilder.line("$varCollector.add($itemVarName)")
      builder.result.append(myInListBuilder.result)
      builder.line("}")
      builder.line("$varName = $varCollector")
    }
    return true
  }

  override fun visitMapStart(varName: String,
                             keyVarName: String,
                             valueVarName: String,
                             keyType: ValueType<*>,
                             valueType: ValueType<*>): Boolean {
    return false
  }

  override fun visitMapEnd(varName: String,
                           keyVarName: String,
                           valueVarName: String,
                           keyType: ValueType<*>,
                           valueType: ValueType<*>,
                           traverseResult: Boolean): Boolean {
    return false
  }

  override fun visitOptionalStart(varName: String, notNullVarName: String, type: ValueType<*>): Boolean {
    if (type.isRefType()) return true

    val sub = LinesBuilder(StringBuilder(), builder.indentLevel + 1)
    builders.add(sub)
    return true
  }

  override fun visitOptionalEnd(varName: String, notNullVarName: String, type: ValueType<*>, traverseResult: Boolean): Boolean {
    if (type.isRefType()) return true

    val popLast = builders.removeLast()
    builder.section("if (de.acceptNull())") {
      line("$varName = null")
    }
    builder.section("else") {
      if (popLast.result.isNotBlank()) {
        line("val $notNullVarName: ${type.javaType}")
        result.append(popLast.result)
        line("$varName = $notNullVarName")
      }
    }
    return true
  }

  override fun visitUnknownBlob(varName: String, javaSimpleName: String): Boolean {
    return false
  }

  override fun visitKnownBlobStart(varName: String, javaSimpleName: String): Boolean {
    return false
  }

  override fun visitKnownBlobFinish(varName: String, javaSimpleName: String, traverseResult: Boolean): Boolean {
    return false
  }

  override fun visitDataClassStart(varName: String, dataClass: ValueType.DataClass<*>): Boolean {
    TODO("Not yet implemented")
  }

  override fun visitDataClassEnd(varName: String, dataClass: ValueType.DataClass<*>, traverseResult: Boolean): Boolean {
    TODO("Not yet implemented")
  }

  private fun counter(): Int {
    return countersCounter++
  }
}

class SerializatorVisitor private constructor(private val linesBuilder: ArrayDeque<LinesBuilder>) : InterfaceVisitor {

  constructor(linesBuilder: LinesBuilder) : this(ArrayDeque<LinesBuilder>().also { it.add(linesBuilder) })

  val builder: LinesBuilder
    get() = linesBuilder.last()

  override fun visitBoolean(varName: String): Boolean {
    builder.line("ser.saveBoolean($varName)")
    return true
  }

  override fun visitInt(varName: String): Boolean {
    builder.line("ser.saveInt($varName)")
    return true
  }

  override fun visitString(varName: String): Boolean {
    builder.line("ser.saveString($varName)")
    return true
  }

  override fun visitListStart(varName: String, itemVarName: String, listArgumentType: ValueType<*>): Boolean {
    if (listArgumentType.isRefType()) return true

    val sub = LinesBuilder(StringBuilder(), builder.indentLevel + 1)
    linesBuilder.add(sub)
    return true
  }

  override fun visitListEnd(varName: String, itemVarName: String, traverseResult: Boolean, listArgumentType: ValueType<*>): Boolean {
    if (listArgumentType.isRefType()) return true

    val myInListBuilder = linesBuilder.removeLast()
    if (myInListBuilder.result.isNotBlank()) {
      builder.line("ser.saveInt($varName.size)")
      builder.line("for ($itemVarName in $varName) {")
      builder.result.append(myInListBuilder.result)
      builder.line("}")
    }
    return true
  }

  override fun visitMapStart(varName: String,
                             keyVarName: String,
                             valueVarName: String,
                             keyType: ValueType<*>,
                             valueType: ValueType<*>): Boolean {
    if (keyType.isRefType() || valueType.isRefType()) return true

    val sub = LinesBuilder(StringBuilder(), builder.indentLevel + 1)
    linesBuilder.add(sub)
    return true
  }

  override fun visitMapEnd(varName: String,
                           keyVarName: String,
                           valueVarName: String,
                           keyType: ValueType<*>,
                           valueType: ValueType<*>,
                           traverseResult: Boolean): Boolean {
    if (keyType.isRefType() || valueType.isRefType()) return true

    val inMapBuilder = linesBuilder.removeLast()
    if (inMapBuilder.result.isNotBlank()) {
      builder.line("ser.saveInt($varName.size)")
      builder.line("for (($keyVarName, $valueVarName) in $varName) {")
      builder.result.append(inMapBuilder.result)
      builder.line("}")
    }
    return true
  }

  override fun visitOptionalStart(varName: String, notNullVarName: String, type: ValueType<*>): Boolean {
    if (type.isRefType()) return true

    val sub = LinesBuilder(StringBuilder(), builder.indentLevel + 1)
    linesBuilder.add(sub)
    return true
  }

  override fun visitOptionalEnd(varName: String, notNullVarName: String, type: ValueType<*>, traverseResult: Boolean): Boolean {
    if (type.isRefType()) return true

    val inMapBuilder = linesBuilder.removeLast()
    if (inMapBuilder.result.isNotBlank()) {
      builder.line("val $notNullVarName = $varName")
      builder.line("if ($notNullVarName != null) {")
      builder.result.append(inMapBuilder.result)
      builder.line("} else {")
      builder.line("    ser.saveNull()")
      builder.line("}")
    }
    return true
  }

  override fun visitUnknownBlob(varName: String, javaSimpleName: String): Boolean {
    if (javaSimpleName == "EntitySource") return true
    builder.line("ser.saveBlob($varName, \"$javaSimpleName\")")
    return true
  }

  override fun visitKnownBlobStart(varName: String, javaSimpleName: String): Boolean {
    return true
  }

  override fun visitKnownBlobFinish(varName: String, javaSimpleName: String, traverseResult: Boolean): Boolean {
    return true
  }

  override fun visitDataClassStart(varName: String, dataClass: ValueType.DataClass<*>): Boolean {
    TODO("Not yet implemented")
  }

  override fun visitDataClassEnd(varName: String, dataClass: ValueType.DataClass<*>, traverseResult: Boolean): Boolean {
    TODO("Not yet implemented")
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

  fun visitDataClassStart(varName: String, dataClass: ValueType.DataClass<*>): Boolean
  fun visitDataClassEnd(varName: String, dataClass: ValueType.DataClass<*>, traverseResult: Boolean): Boolean
}

