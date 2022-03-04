package org.jetbrains.deft.collections

import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.RelationsBuilder
import org.jetbrains.deft._Obj0
import org.jetbrains.deft._Obj1
import org.jetbrains.deft.impl.ObjImpl
import org.jetbrains.deft.impl.ObjType1

@Suppress("CONFLICTING_INHERITED_JVM_DECLARATIONS")
class Rels(
    owner: ObjImpl,
    val relFactory: ObjType1<ObjImpl, ObjBuilder<ObjImpl>, ObjImpl>,
) : Refs(owner), RelationsBuilder<_Obj0, ObjImpl, _Obj1, _Obj1> {
    override fun createRelation(target: _Obj1): ObjImpl = relFactory.invoke(target as ObjImpl)

    companion object {
        fun new(
            owner: ObjImpl,
            relFactory: ObjType1<*, *, *>,
        ) = Rels(owner, relFactory as ObjType1<ObjImpl, ObjBuilder<ObjImpl>, ObjImpl>)
    }

}