// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.workspaceModel.codegen.InterfaceTraverser
import com.intellij.workspaceModel.codegen.InterfaceVisitor
import com.intellij.workspaceModel.codegen.deft.meta.Obj
import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.devkit.workspaceModel.metaModel.impl.ObjClassImpl
import com.intellij.devkit.workspaceModel.metaModel.impl.ObjModuleImpl
import com.intellij.devkit.workspaceModel.metaModel.impl.OwnPropertyImpl
import org.jetbrains.kotlin.descriptors.SourceElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InterfaceTraverserTest {
  @Test
  fun `test boolean`() {
    val type = createType()

    type.addField("myName", ValueType.Boolean)

    val collector = StringBuilder()
    InterfaceTraverser().traverse(type, MyInterfaceVisitor(collector))
    assertEquals("- Boolean - myName\n", collector.toString())
  }

  private fun ObjClassImpl<Obj>.addField(name: String, type: ValueType<*>) {
    addField(OwnPropertyImpl(this, name, type, ObjProperty.ValueKind.Plain, false, false, false, 0, false, SourceElement.NO_SOURCE))
  }

  @Test
  fun `test int`() {
    val type = createType()

    type.addField("myName", ValueType.Int)

    val collector = StringBuilder()
    InterfaceTraverser().traverse(type, MyInterfaceVisitor(collector))
    assertEquals("- Int - myName\n", collector.toString())
  }

  @Test
  fun `test string`() {
    val type = createType()

    type.addField("myName", ValueType.String)

    val collector = StringBuilder()
    InterfaceTraverser().traverse(type, MyInterfaceVisitor(collector))
    assertEquals("- String - myName\n", collector.toString())
  }

  @Test
  fun `test list`() {
    val type = createType()

    type.addField("myName", ValueType.List(ValueType.String))

    val collector = StringBuilder()
    InterfaceTraverser().traverse(type, MyInterfaceVisitor(collector))
    assertEquals("""
      -- Start loop --
      - String - _myName
      -- Finish loop --
      
    """.trimIndent(), collector.toString())
  }

  @Test
  fun `test map`() {
    val type = createType()

    type.addField("myName", ValueType.Map(ValueType.String, ValueType.Int))

    val collector = StringBuilder()
    InterfaceTraverser().traverse(type, MyInterfaceVisitor(collector))
    assertEquals("""
      -- Start Map --
      - String - key_myName
      - Int - value_myName
      -- Finish Map --
      
    """.trimIndent(), collector.toString())
  }

  @Test
  fun `test optional`() {
    val type = createType()

    type.addField("myName", ValueType.Optional(ValueType.Int))

    val collector = StringBuilder()
    InterfaceTraverser().traverse(type, MyInterfaceVisitor(collector))
    assertEquals("""
      -- Start Optional --
      - Int - _myName
      -- Finish Optional --
      
    """.trimIndent(), collector.toString())
  }

  @Test
  fun `test blob`() {
    val type = createType()

    type.addField("myName", ValueType.Blob<Any>("my.class", emptyList()))

    val collector = StringBuilder()
    InterfaceTraverser().traverse(type, MyInterfaceVisitor(collector))
    assertEquals("""
      -- Blob | myName | my.class --
      
    """.trimIndent(), collector.toString())
  }

  private fun createType(): ObjClassImpl<Obj> {
    return ObjClassImpl(ObjModuleImpl("module"), "MyClass", ObjClass.Openness.final, SourceElement.NO_SOURCE)
  }
}

class MyInterfaceVisitor(val collector: StringBuilder) : InterfaceVisitor {
  override fun visitBoolean(varName: String): Boolean {
    collector.appendLine("- Boolean - $varName")
    return true
  }

  override fun visitInt(varName: String): Boolean {
    collector.appendLine("- Int - $varName")
    return true
  }

  override fun visitString(varName: String): Boolean {
    collector.appendLine("- String - $varName")
    return true
  }

  override fun visitListStart(varName: String, itemVarName: String, listArgumentType: ValueType<*>): Boolean {
    collector.appendLine("-- Start loop --")
    return true
  }

  override fun visitListEnd(varName: String, itemVarName: String, traverseResult: Boolean, listArgumentType: ValueType<*>): Boolean {
    collector.appendLine("-- Finish loop --")
    return true
  }

  override fun visitMapStart(varName: String,
                             keyVarName: String,
                             valueVarName: String,
                             keyType: ValueType<*>,
                             valueType: ValueType<*>): Boolean {
    collector.appendLine("-- Start Map --")
    return true
  }

  override fun visitMapEnd(varName: String,
                           keyVarName: String,
                           valueVarName: String,
                           keyType: ValueType<*>,
                           valueType: ValueType<*>,
                           traverseResult: Boolean): Boolean {
    collector.appendLine("-- Finish Map --")
    return true
  }

  override fun visitOptionalStart(varName: String, notNullVarName: String, type: ValueType<*>): Boolean {
    collector.appendLine("-- Start Optional --")
    return true
  }

  override fun visitOptionalEnd(varName: String, notNullVarName: String, type: ValueType<*>, traverseResult: Boolean): Boolean {
    collector.appendLine("-- Finish Optional --")
    return true
  }

  override fun visitUnknownBlob(varName: String, javaSimpleName: String): Boolean {
    collector.appendLine("-- Blob | $varName | $javaSimpleName --")
    return true
  }

  override fun visitKnownBlobStart(varName: String, javaSimpleName: String): Boolean {
    TODO("Not yet implemented")
  }

  override fun visitKnownBlobFinish(varName: String, javaSimpleName: String, traverseResult: Boolean): Boolean {
    TODO("Not yet implemented")
  }

  override fun visitDataClassStart(varName: String, dataClass: ValueType.DataClass<*>): Boolean {
    TODO("Not yet implemented")
  }

  override fun visitDataClassEnd(varName: String, dataClass: ValueType.DataClass<*>, traverseResult: Boolean): Boolean {
    TODO("Not yet implemented")
  }
}
