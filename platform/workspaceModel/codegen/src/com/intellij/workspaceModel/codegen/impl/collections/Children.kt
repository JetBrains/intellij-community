package org.jetbrains.deft.collections

import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.ObjImpl

@Suppress("CONFLICTING_INHERITED_JVM_DECLARATIONS")
class Children(
    owner: ObjImpl,
) : Refs(owner) {
    constructor(owner: ObjImpl, value: Collection<Obj>) : this(owner) {
        this.addAll(value as Collection<ObjImpl>)
    }

    override fun onChange(old: ObjImpl?, element: ObjImpl?) {
//        old?._parent = null
//        element?._parent = owner
        super.onChange(old, element)
    }
}