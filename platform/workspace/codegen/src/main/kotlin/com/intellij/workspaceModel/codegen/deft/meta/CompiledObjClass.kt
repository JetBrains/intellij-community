// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.deft.meta

interface CompiledObjModule : ObjModule {
  fun objClass(typeId: Int): ObjClass<*>
  fun <T : Obj, V> extField(receiver: ObjClass<*>, name: String, default: T.() -> V): ExtProperty<T, V>
}