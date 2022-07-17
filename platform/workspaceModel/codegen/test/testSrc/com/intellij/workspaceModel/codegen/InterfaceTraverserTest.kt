// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen

import com.intellij.workspaceModel.codegen.deft.model.*
import com.intellij.workspaceModel.codegen.deft.*
import com.intellij.workspaceModel.codegen.deft.Field
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class InterfaceTraverserTest {
  @Test
  fun `test boolean`() {
    val type = createType()

    Field(type, "myName", TBoolean)

    val collector = StringBuilder()
    InterfaceTraverser(emptyList()).traverse(type, MyInterfaceVisitor(collector))
    assertEquals("- Boolean - myName\n", collector.toString())
  }

  @Test
  fun `test int`() {
    val type = createType()

    Field(type, "myName", TInt)

    val collector = StringBuilder()
    InterfaceTraverser(emptyList()).traverse(type, MyInterfaceVisitor(collector))
    assertEquals("- Int - myName\n", collector.toString())
  }

  @Test
  fun `test string`() {
    val type = createType()

    Field(type, "myName", TString)

    val collector = StringBuilder()
    InterfaceTraverser(emptyList()).traverse(type, MyInterfaceVisitor(collector))
    assertEquals("- String - myName\n", collector.toString())
  }

  @Test
  fun `test list`() {
    val type = createType()

    Field(type, "myName", TList(TString))

    val collector = StringBuilder()
    InterfaceTraverser(emptyList()).traverse(type, MyInterfaceVisitor(collector))
    assertEquals("""
      -- Start loop --
      - String - _myName
      -- Finish loop --
      
    """.trimIndent(), collector.toString())
  }

  @Test
  fun `test map`() {
    val type = createType()

    Field(type, "myName", TMap(TString, TInt))

    val collector = StringBuilder()
    InterfaceTraverser(emptyList()).traverse(type, MyInterfaceVisitor(collector))
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

    Field(type, "myName", TOptional(TInt))

    val collector = StringBuilder()
    InterfaceTraverser(emptyList()).traverse(type, MyInterfaceVisitor(collector))
    assertEquals("""
      -- Start Optional --
      - Int - _myName
      -- Finish Optional --
      
    """.trimIndent(), collector.toString())
  }

  @Test
  fun `test blob`() {
    val type = createType()

    Field(type, "myName", TBlob<Any>("my.class"))

    val collector = StringBuilder()
    InterfaceTraverser(emptyList()).traverse(type, MyInterfaceVisitor(collector))
    assertEquals("""
      -- Blob | myName | my.class --
      
    """.trimIndent(), collector.toString())
  }

  private fun createType(): DefType {
    val module = KtObjModule(null)
    return DefType(module, "myName", null,
                   KtInterface(module, KtScope(null, null), SrcRange(Src("X") { "xyz" }, 0..1), emptyList(), null, null))
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

  override fun visitDataClassStart(varName: String, javaSimpleName: String, foundType: DefType): Boolean {
    TODO("Not yet implemented")
  }

  override fun visitDataClassEnd(varName: String, javaSimpleName: String, traverseResult: Boolean, foundType: DefType): Boolean {
    TODO("Not yet implemented")
  }
}
