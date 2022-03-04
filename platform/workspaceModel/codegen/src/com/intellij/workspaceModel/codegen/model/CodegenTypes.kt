package org.jetbrains.deft.codegen.model

import deft.storage.codegen.suspendable
import org.jetbrains.deft.impl.ObjModule
import org.jetbrains.deft.impl.fields.MemberOrExtField
import org.jetbrains.deft.obj.impl.fields.MetaTypes
import org.jetbrains.deft.runtime.Runtime

object CodegenTypes: ObjModule(Id("org.jetbrains.deft.Codegen")) {
    @InitApi
    override fun init() {
        requireDependency(Runtime)
        requireDependency(MetaTypes)

        beginExtFieldsInit(1)
        registerExtField(MemberOrExtField.Companion.suspendable)
    }
}