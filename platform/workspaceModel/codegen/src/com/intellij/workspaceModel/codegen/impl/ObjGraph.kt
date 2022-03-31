// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl

import org.jetbrains.deft.ObjId
import org.jetbrains.deft.impl.ObjImpl
import org.jetbrains.deft.impl.ObjName
import org.jetbrains.deft.impl.ObjNamesImpl
import org.jetbrains.deft.obj.impl.ObjImplWrapper

class ObjGraph(var version: Int, var dataViewId: Int, names: List<ObjName>) {
  /* internal */var disallowLoad = false
  val names = ObjNamesImpl(names)

  var owner: SnapshotView? = null
  private val loaded = HashMap<ObjId<*>, ObjImplWrapper>()

  fun <T> getOrLoad(id: ObjId<T>): T {
    check(!id.isNothing())
    return loaded.getOrPut(id) { load(id) as ObjImplWrapper } as T
  }

  /* internal */fun _register(obj: ObjImpl) {
    val id = obj._id
    if (id.isValid()) {
      check(id != ObjId.nothing)
      val old = loaded.putIfAbsent(id, obj)
      check(old == null) { "$obj: $id already used by $old" }
      names.add(obj) // todo: implement on remote
    }
  }

  fun _setParent(oldParent: ObjImpl?, newParent: ObjImpl?, objImpl: ObjImpl) {
    names.move(objImpl, oldParent, newParent)
  }

  fun _rename(obj: ObjImpl, old: String?, new: String?) {
    names.rename(obj, old, new)
  }

  private fun <T> load(id: ObjId<T>): T {
    check(!disallowLoad) { "Load of obj $id is disallowed" }
    //val owner = owner!!
    //when (val result = data.load(id, owner.viewId)) {
    //  DataStorage.LoadResult.OutdatedSnapshot -> {
    //    owner.close()
    //    throw OutdatedSnapshot()
    //  }
    //  is DataStorage.LoadResult.Loaded -> return result.toObj(this) as T
    //}
    throw Exception("")
  }

  override fun toString(): String = "v${version}@${dataViewId}"

  fun dump(sb: StringBuilder) {
    loaded.values.forEach {
      sb.append("  ${it.impl._id} ${it.name}\n")
    }
  }
}