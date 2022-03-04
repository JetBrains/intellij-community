package org.jetbrains.deft.codegen.ijws

import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.intellijWs.Ij
import storage.codegen.patcher.def

val Field<out Obj, Any?>.ijName: String
    get() {
        val customName = def?.annotations
            ?.byName?.get(Ij::class.simpleName)
            ?.args?.firstOrNull()?.text?.removeSurrounding("\"")
        return customName ?: name
    }