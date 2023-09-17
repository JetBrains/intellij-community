// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.deft.meta

import com.intellij.workspaceModel.codegen.deft.annotations.Name

interface ObjModule : Obj {
  @Name
  val name: String

  val moduleId: Id
    get() = Id(name)

  val dependencies: List<ObjModule>

  val types: List<ObjClass<*>>

  val abstractTypes: List<ValueType.AbstractClass<*>>

  val extensions: List<ExtProperty<*, *>>

  /**
   * Example: `com.intellij.platform.workspace.storage.obj.intellijWs`
   * will be parsed as:
   * - `com.intellij.platform.workspace.storage.obj.intellijWs` package
   * - `com.intellij.platform.workspace.storage.obj.intellijWs.IntellijWs` object name
   **/
  @JvmInline
  value class Id(private val notation: String) {
    private val javaPackage: String
      get() = notation

    private val objName: String
      get() = notation.substringAfterLast(".")
        .replaceFirstChar { it.titlecaseChar() }

    val objFqn: String
      get() = "$javaPackage.$objName"

    fun check() {
      check(objName.first().isUpperCase()) {
        "`$notation` should be an id notation like `com.intellij.platform.workspace.storage.obj.IntellijWs`.\n" +
        "Will be parsed as:\n" +
        "- `com.intellij.platform.workspace.storage.obj.intellijWs` package\n" +
        "- `IntellijWs` object name"
      }
    }

    override fun toString(): String = "ObjModule.Id($notation)"
  }
}