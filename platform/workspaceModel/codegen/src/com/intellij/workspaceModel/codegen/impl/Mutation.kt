// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl

import org.jetbrains.deft.Obj
import org.jetbrains.deft.Root
import org.jetbrains.deft.RootImpl
import org.jetbrains.deft.bytes.logUpdates
import org.jetbrains.deft.impl.ObjImpl
import org.jetbrains.deft.impl.ObjType
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.obj.api.extensible.Extensible

class Mutation(
  graph: ObjGraph
) : SnapshotView(graph),
    Root.Builder {
  val rootBuilder: Root.Builder
    get() = RootImpl.Builder(root)

  override val factory: ObjType<Root, *> get() = Root
  override val name: String? get() = null
  override val parent: Obj? get() = null
  override fun <V> setValue(field: Field<in Root, V>, value: V) = Unit
  override fun getExtensibleContainer(): Extensible = rootBuilder.getExtensibleContainer()
  override fun build(): Root = rootBuilder.build()

  private val changed = mutableSetOf<ObjImpl>()

  fun add(target: ObjImpl) {
    target.ensureInGraph(graph)
  }

  fun markChanged(obj: ObjImpl) {
    changed.add(obj)
  }

  //suspend fun commit(): StoreResult {
  //  if (!logUpdates) graph.disallowLoad = true
  //  changed.forEach {
  //    it.freeze()
  //  }
  //  return data.update(graph.version, changed.toList())
  //}
}
