package org.jetbrains.deft.obj.impl.fields

import org.jetbrains.deft.impl.ObjModule
import org.jetbrains.deft.impl.fields.MemberOrExtField

object MetaTypes : ObjModule(Id("org.jetbrains.deft.impl.fields")) {
    @InitApi
    override fun init() {
        beginInit(1)
        add(MemberOrExtField)
    }
}